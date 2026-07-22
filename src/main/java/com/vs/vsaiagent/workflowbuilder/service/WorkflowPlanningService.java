package com.vs.vsaiagent.workflowbuilder.service;

import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.registry.ToolRegistry;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowEdge;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 自然语言需求 → Workflow IR（MVP：规则生成，不调用大模型）。
 *
 * 固定生成 Start → [Tool?] → LLM → Answer 串行流程，
 * 用户 requirement 整体作为 LLM 节点 instruction；
 * 若 requirement 命中内置关键词表中的工具/技能，会在 Start 之后插入一个
 * Tool 节点，调用 Agent 工作台已注册的工具或 Skill。
 *
 * 二期：升级为 Spring AI Planner（LLM 输出 JSON IR，规则生成作为兜底）。
 */
@Service
public class WorkflowPlanningService {

    static final String LLM_NODE_ID = "llm_task";
    static final String TOOL_NODE_ID = "tool_call";
    private static final int NAME_MAX_LENGTH = 20;

    /**
     * 关键词 → 能力映射表。kind 为 "tool" 或 "skill"，name 为
     * ToolRegistry/SkillRegistry 中注册的名字。
     */
    private static final List<CapabilityRule> CAPABILITY_RULES = List.of(
            new CapabilityRule("tool", "web_search", "网页搜索", "联网搜索", "查资料", "查找资料", "搜索网络"),
            new CapabilityRule("tool", "image_search", "图片搜索", "找图片", "搜图"),
            new CapabilityRule("skill", "pdf-generation", "生成PDF", "导出PDF", "PDF报告", "生成pdf", "导出pdf"),
            // 摄影
            new CapabilityRule("tool", "exposure_advisor", "曝光", "拍摄参数", "测光", "exposure"),
            // 咖啡（品鉴更具体，排在配方前）
            new CapabilityRule("skill", "coffee-tasting-notes", "品鉴", "风味笔记", "豆子风味"),
            new CapabilityRule("tool", "coffee_recipe", "咖啡", "冲煮", "手冲", "意式", "拿铁", "espresso"),
            // 出行通用（天气/预算/景点，更具体的关键词排在前面）
            new CapabilityRule("tool", "trip_weather", "天气", "气温", "下雨", "温差", "weather"),
            new CapabilityRule("tool", "trip_budget", "预算", "花费", "费用", "多少钱", "budget"),
            new CapabilityRule("tool", "scenic_spots", "景点", "打卡", "沿途", "好玩", "景区"),
            // 摩旅（装备清单更具体，排在行程规划前）
            new CapabilityRule("skill", "moto-gear-checklist", "装备清单", "打包清单", "行装"),
            new CapabilityRule("tool", "moto_trip_planner", "摩托", "摩旅", "骑行", "机车", "motorcycle")
    );

    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;

    /** LLM 规划器，可为 null（纯规则模式 / 单测）。 */
    private final WorkflowPlanner llmPlanner;
    /** llm | rule，默认 llm；llm 失败自动回退 rule。 */
    private final String plannerMode;

    /** 纯规则构造器：供单元测试与"无大模型"场景使用。 */
    public WorkflowPlanningService(ToolRegistry toolRegistry, SkillRegistry skillRegistry) {
        this(toolRegistry, skillRegistry, null, "rule");
    }

    /** Spring 注入构造器：默认启用 LLM 规划，失败回退规则。 */
    @Autowired
    public WorkflowPlanningService(ToolRegistry toolRegistry,
                                   SkillRegistry skillRegistry,
                                   WorkflowPlanner llmPlanner,
                                   @Value("${app.workflow-builder.planner:llm}") String plannerMode) {
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
        this.llmPlanner = llmPlanner;
        this.plannerMode = plannerMode;
    }

    /** 规划结果 + 诊断信息（诊断供「错误分析审计」暴露：用了哪个规划器、LLM 失败原因等）。 */
    public record PlanResult(WorkflowIR ir, List<String> diagnostics) {
    }

    public WorkflowIR plan(String requirement) {
        return planDetailed(requirement).ir();
    }

    /**
     * 规划并收集诊断。优先 LLM 规划，失败 / 不可用时回退规则规划（始终产出可用结果），
     * 但把"用了哪个规划器、为什么回退"写进 diagnostics，不再静默吞掉。
     */
    public PlanResult planDetailed(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("requirement 不能为空");
        }
        String trimmed = requirement.trim();
        List<String> diagnostics = new ArrayList<>();

        if ("llm".equalsIgnoreCase(plannerMode) && llmPlanner != null) {
            Optional<WorkflowIR> llmResult = llmPlanner.plan(trimmed, diagnostics);
            if (llmResult.isPresent()) {
                diagnostics.add("规划器：LLM");
                return new PlanResult(llmResult.get(), diagnostics);
            }
            diagnostics.add("规划器：规则（已从 LLM 回退）");
        } else {
            diagnostics.add("规划器：规则");
        }
        return new PlanResult(planByRule(trimmed), diagnostics);
    }

    /**
     * 规则规划（关键词命中插入工具/技能节点）：作为 LLM 规划的兜底，亦可单独使用。
     */
    public WorkflowIR planByRule(String trimmed) {
        String name = generateName(trimmed);

        WorkflowNode start = WorkflowNode.start();
        WorkflowNode answer = WorkflowNode.answer();
        Optional<WorkflowNode> toolNode = detectToolNode(trimmed);

        List<WorkflowNode> nodes = new ArrayList<>();
        List<WorkflowEdge> edges = new ArrayList<>();
        nodes.add(start);
        String previousId = start.id();

        if (toolNode.isPresent()) {
            WorkflowNode tool = toolNode.get();
            nodes.add(tool);
            edges.add(new WorkflowEdge(previousId, tool.id()));
            previousId = tool.id();
        }

        WorkflowNode llm = WorkflowNode.llm(LLM_NODE_ID, name, buildInstruction(trimmed, toolNode.isPresent()));
        nodes.add(llm);
        edges.add(new WorkflowEdge(previousId, llm.id()));
        edges.add(new WorkflowEdge(llm.id(), answer.id()));
        nodes.add(answer);

        return new WorkflowIR(UUID.randomUUID().toString(), name, trimmed, nodes, edges);
    }

    /**
     * 根据 requirement 命中的关键词，在已注册的工具/技能中查找对应能力，
     * 并构造一个 tool 节点。未命中或能力未注册时返回 empty。
     */
    private Optional<WorkflowNode> detectToolNode(String requirement) {
        for (CapabilityRule rule : CAPABILITY_RULES) {
            if (!containsAny(requirement, rule.keywords())) {
                continue;
            }
            if ("tool".equals(rule.kind())) {
                Optional<ToolMetadata> meta = toolRegistry.findByName(rule.name()).map(t -> t.metadata());
                if (meta.isEmpty()) {
                    continue;
                }
                String param = firstNonBlank(meta.get().getRequiredParams(), "query");
                String title = nonBlank(meta.get().getDisplayName(), rule.name());
                String argsTemplate = "{\"" + param + "\": \"${start}\"}";
                return Optional.of(WorkflowNode.tool(TOOL_NODE_ID, title, "tool:" + rule.name(), argsTemplate));
            } else {
                Optional<SkillMetadata> meta = skillRegistry.find(rule.name()).map(s -> s.metadata());
                if (meta.isEmpty()) {
                    continue;
                }
                String param = firstRequiredInput(meta.get().inputs(), "input");
                String title = nonBlank(meta.get().displayName(), rule.name());
                String argsTemplate = "{\"" + param + "\": \"${start}\"}";
                return Optional.of(WorkflowNode.tool(TOOL_NODE_ID, title, "skill:" + rule.name(), argsTemplate));
            }
        }
        return Optional.empty();
    }

    private String firstNonBlank(List<String> values, String fallback) {
        if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).isBlank()) {
            return values.get(0);
        }
        return fallback;
    }

    private String firstRequiredInput(List<SkillParam> inputs, String fallback) {
        if (inputs == null) {
            return fallback;
        }
        for (SkillParam p : inputs) {
            if (p.required() && p.name() != null && !p.name().isBlank()) {
                return p.name();
            }
        }
        if (!inputs.isEmpty() && inputs.get(0).name() != null && !inputs.get(0).name().isBlank()) {
            return inputs.get(0).name();
        }
        return fallback;
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record CapabilityRule(String kind, String name, String... keywords) {
    }

    /**
     * 规则命名：按关键词识别三类内置任务，否则截断需求作为名称。
     */
    private String generateName(String requirement) {
        if (containsAny(requirement, "总结", "摘要", "概括", "要点")
                && !containsAny(requirement, "提取", "抽取")) {
            return "文本总结工作流";
        }
        if (containsAny(requirement, "提取", "抽取", "解析出")) {
            return "信息提取工作流";
        }
        if (containsAny(requirement, "问答", "知识库", "答疑")) {
            return "文档问答工作流";
        }
        String head = requirement.length() > NAME_MAX_LENGTH
                ? requirement.substring(0, NAME_MAX_LENGTH)
                : requirement;
        return head + "工作流";
    }

    /**
     * LLM 节点 instruction：需求原文 + 通用输出约束。
     * hasToolResult 为 true 时，在前面追加对工具/技能执行结果的引用说明。
     */
    private String buildInstruction(String requirement, boolean hasToolResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个任务执行助手。请严格按照下面的任务要求处理用户输入。\n\n");
        if (hasToolResult) {
            sb.append("以下是已执行的工具/技能结果，请结合该结果回答：\n${tool_call}\n\n");
        }
        sb.append("任务要求：").append(requirement).append("\n\n");
        if (containsAny(requirement, "JSON", "json")) {
            sb.append("输出要求：只输出合法 JSON，不要包含 markdown 代码块标记或多余解释。");
        } else {
            sb.append("输出要求：结构清晰、关键结论先行，不要编造内容。");
        }
        return sb.toString();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
