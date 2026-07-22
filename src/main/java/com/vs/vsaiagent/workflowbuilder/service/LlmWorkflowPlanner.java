package com.vs.vsaiagent.workflowbuilder.service;

import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.registry.ToolRegistry;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import com.vs.vsaiagent.workflowbuilder.model.ValidateResult;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowEdge;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于大模型的 Workflow 规划器（简历"自然语言驱动 Workflow Builder"的核心）。
 *
 * 分工原则——LLM 负责「语义决策」，Java 负责「结构正确」：
 *  - LLM 输出 {@link LlmPlan}：工作流名称、有序的能力调用列表（tool/skill）、给 LLM 节点的核心指令；
 *  - Java {@link #assemble} 据此确定性地拼装 start → [tool...] → llm → answer 的 IR，
 *    自动生成节点 id、边、变量引用，保证图结构一定合法（无环、start/answer 齐全）；
 *  - 能力引用会与 {@link ToolRegistry}/{@link SkillRegistry} 实际注册项核对，未注册的直接丢弃，
 *    避免大模型臆造不存在的工具；
 *  - 最终再用 {@link WorkflowDslValidateService#validateIr} 兜底校验，不过则返回 empty，
 *    交由 {@link WorkflowPlanningService} 回退到规则规划。
 */
@Slf4j
@Component
public class LlmWorkflowPlanner implements WorkflowPlanner {

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;
    private final WorkflowDslValidateService validateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmWorkflowPlanner(ChatModel chatModel,
                              ToolRegistry toolRegistry,
                              SkillRegistry skillRegistry,
                              WorkflowDslValidateService validateService) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
        this.validateService = validateService;
    }

    /**
     * 单个能力调用：ref 形如 "tool:milkyway_rise" / "skill:pdf-generation"，
     * arguments 为该能力的完整参数（由 LLM 按需求推断具体值；某参数应取运行时用户输入时填 "${start}"）。
     */
    public record CapabilityCall(
            String ref,
            Map<String, Object> arguments
    ) {
    }

    /** LLM 结构化输出：只做语义决策，不直接产出图结构 / id / 边。 */
    public record LlmPlan(
            String name,
            List<CapabilityCall> capabilities,
            String llmInstruction
    ) {
    }

    @Override
    public Optional<WorkflowIR> plan(String requirement) {
        return plan(requirement, new ArrayList<>());
    }

    @Override
    public Optional<WorkflowIR> plan(String requirement, List<String> diagnostics) {
        if (requirement == null || requirement.isBlank()) {
            diagnostics.add("LLM 规划：需求为空，跳过");
            return Optional.empty();
        }
        String trimmed = requirement.trim();
        try {
            LlmPlan plan = chatClient.prompt()
                    .system(systemPrompt())
                    .user(userPrompt(trimmed))
                    .call()
                    .entity(LlmPlan.class);
            if (plan == null) {
                diagnostics.add("LLM 规划：模型未产出结构化 plan（返回 null）");
                return Optional.empty();
            }
            WorkflowIR ir = assemble(plan, trimmed);
            ValidateResult vr = validateService.validateIr(ir);
            if (!vr.valid()) {
                String msg = "LLM 规划的 IR 校验未通过：" + vr.errors();
                log.warn("[llm-planner] {}，回退规则规划", msg);
                diagnostics.add(msg);
                return Optional.empty();
            }
            log.info("[llm-planner] 规划成功 name={} nodes={}", ir.name(), ir.nodes().size());
            return Optional.of(ir);
        } catch (Exception e) {
            String msg = "LLM 规划异常（" + e.getClass().getSimpleName() + "）：" + e.getMessage();
            log.warn("[llm-planner] {}，回退规则规划", msg);
            diagnostics.add(msg);
            return Optional.empty();
        }
    }

    /**
     * 把 LLM 的语义决策确定性地拼装为合法 IR。包级可见，便于单测不依赖大模型直接验证拼装逻辑。
     */
    WorkflowIR assemble(LlmPlan plan, String requirement) {
        WorkflowNode start = WorkflowNode.start();
        List<WorkflowNode> nodes = new ArrayList<>();
        List<WorkflowEdge> edges = new ArrayList<>();
        nodes.add(start);
        String previousId = start.id();

        List<String> toolNodeIds = new ArrayList<>();
        int index = 1;
        List<CapabilityCall> capabilities = plan.capabilities() == null ? List.of() : plan.capabilities();
        for (CapabilityCall capability : capabilities) {
            Optional<WorkflowNode> toolNode = buildCapabilityNode(capability, "tool_call_" + index);
            if (toolNode.isEmpty()) {
                continue;
            }
            WorkflowNode tn = toolNode.get();
            nodes.add(tn);
            edges.add(new WorkflowEdge(previousId, tn.id()));
            previousId = tn.id();
            toolNodeIds.add(tn.id());
            index++;
        }

        String name = nonBlank(plan.name(), defaultName(requirement));
        WorkflowNode llm = WorkflowNode.llm(WorkflowPlanningService.LLM_NODE_ID, name,
                buildInstruction(requirement, plan.llmInstruction(), toolNodeIds));
        nodes.add(llm);
        edges.add(new WorkflowEdge(previousId, llm.id()));

        WorkflowNode answer = WorkflowNode.answer();
        edges.add(new WorkflowEdge(llm.id(), answer.id()));
        nodes.add(answer);

        return new WorkflowIR(UUID.randomUUID().toString(), name, requirement, nodes, edges);
    }

    /**
     * 把能力调用解析为 tool 节点；未注册或格式非法返回 empty（丢弃，不让 LLM 臆造的能力进入 IR）。
     * 工具参数优先使用 LLM 给出的完整 arguments；缺省时退回"首个必填参数绑定 ${start}"。
     */
    private Optional<WorkflowNode> buildCapabilityNode(CapabilityCall call, String nodeId) {
        if (call == null || call.ref() == null || call.ref().isBlank()) {
            return Optional.empty();
        }
        String cap = call.ref().trim();
        if (cap.startsWith("tool:")) {
            String name = cap.substring("tool:".length());
            return toolRegistry.findByName(name).map(t -> t.metadata()).map(meta -> {
                String fallbackParam = firstNonBlank(meta.getRequiredParams(), "query");
                String title = nonBlank(meta.getDisplayName(), name);
                return WorkflowNode.tool(nodeId, title, "tool:" + name, argsTemplate(call.arguments(), fallbackParam));
            });
        }
        if (cap.startsWith("skill:")) {
            String name = cap.substring("skill:".length());
            return skillRegistry.find(name).map(Skill::metadata).map(meta -> {
                String fallbackParam = firstRequiredInput(meta.inputs(), "input");
                String title = nonBlank(meta.displayName(), name);
                return WorkflowNode.tool(nodeId, title, "skill:" + name, argsTemplate(call.arguments(), fallbackParam));
            });
        }
        return Optional.empty();
    }

    /**
     * 工具/技能节点的参数模板（JSON 文本）。LLM 给出 arguments 时直接序列化为完整参数，
     * 否则退回单参数 {@code {"<首参>": "${start}"}}（保留无 LLM 参数时的可用性）。
     */
    private String argsTemplate(Map<String, Object> arguments, String fallbackParam) {
        if (arguments != null && !arguments.isEmpty()) {
            try {
                return objectMapper.writeValueAsString(arguments);
            } catch (Exception e) {
                log.warn("[llm-planner] 序列化工具参数失败，退回单参数模板: {}", e.getMessage());
            }
        }
        return "{\"" + fallbackParam + "\": \"${start}\"}";
    }

    /**
     * 组织 LLM 节点指令：核心任务 + 对前序工具/技能结果的引用 + 输出约束。
     * 工具结果引用由 Java 按真实节点 id 生成（支持多个），变量替换在本地试运行时完成。
     */
    private String buildInstruction(String requirement, String llmInstruction, List<String> toolNodeIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个任务执行助手。请严格按照下面的任务要求处理用户输入。\n\n");
        if (!toolNodeIds.isEmpty()) {
            sb.append("以下是已执行的工具/技能结果，请结合这些结果回答：\n");
            for (String id : toolNodeIds) {
                sb.append("- ${").append(id).append("}\n");
            }
            sb.append("\n");
        }
        sb.append("任务要求：").append(nonBlank(llmInstruction, requirement)).append("\n\n");
        if (requirement.contains("JSON") || requirement.contains("json")) {
            sb.append("输出要求：只输出合法 JSON，不要包含 markdown 代码块标记或多余解释。");
        } else {
            sb.append("输出要求：结构清晰、关键结论先行，不要编造内容。");
        }
        return sb.toString();
    }

    private String systemPrompt() {
        return """
                你是一个工作流规划助手。把用户的自然语言需求解析为一个串行任务流程，并以 JSON 输出。
                输出字段：
                  - name: 简短的工作流名称（不超过 20 字）
                  - capabilities: 需要按顺序调用的能力数组；若任务无需外部能力则给空数组。
                    数组每个元素是对象：
                      {
                        "ref": "能力引用，必须严格取自下面【可用能力】列表（形如 tool:web_search / skill:pdf-generation）",
                        "arguments": { 该能力的参数键值对 }
                      }
                    arguments 要求：为该能力列出的【每个参数】都填上具体值——
                      · 能从用户需求直接推断的就给具体值（例如城市"北京"→ latitude 39.9, longitude 116.4；
                        "今晚/今天"→ 用下方给出的当前日期；焦距/光圈等给合理数值）；
                      · 若某参数应由运行时用户输入提供，则该参数的值填字符串 "${start}"；
                      · 不要遗漏列表中标注的参数，也不要编造列表里没有的参数名。
                  - llmInstruction: 给最终 LLM 处理节点的核心任务指令（用一句话概括要做什么）
                只能引用列表中真实存在的能力，不要臆造。最终流程会被固定为 开始 →[能力...]→ LLM 处理 → 回复。
                """;
    }

    private String userPrompt(String requirement) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前日期：").append(LocalDate.now()).append("\n\n");
        sb.append("可用能力（括号内为该能力的参数名）：\n");
        for (ToolMetadata meta : toolRegistry.listMetadata()) {
            sb.append("- tool:").append(meta.getToolName())
                    .append(" (参数: ").append(joinParams(meta.getRequiredParams())).append(")")
                    .append(" —— ").append(nonBlank(meta.getDescription(), "")).append("\n");
        }
        for (Skill skill : skillRegistry.listAll()) {
            SkillMetadata meta = skill.metadata();
            sb.append("- skill:").append(meta.name())
                    .append(" (参数: ").append(joinSkillParams(meta.inputs())).append(")")
                    .append(" —— ").append(nonBlank(meta.description(), "")).append("\n");
        }
        sb.append("\n用户需求：").append(requirement);
        return sb.toString();
    }

    private String joinParams(List<String> params) {
        return params == null || params.isEmpty() ? "无" : String.join(", ", params);
    }

    private String joinSkillParams(List<SkillParam> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return "无";
        }
        List<String> names = new ArrayList<>();
        for (SkillParam p : inputs) {
            if (p.name() != null && !p.name().isBlank()) {
                names.add(p.required() ? p.name() : p.name() + "(可选)");
            }
        }
        return names.isEmpty() ? "无" : String.join(", ", names);
    }

    private String defaultName(String requirement) {
        String head = requirement.length() > 20 ? requirement.substring(0, 20) : requirement;
        return head + "工作流";
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
}
