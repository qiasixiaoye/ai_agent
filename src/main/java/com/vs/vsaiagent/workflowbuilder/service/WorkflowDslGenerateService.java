package com.vs.vsaiagent.workflowbuilder.service;

import com.vs.vsaiagent.agentplatform.registry.ToolRegistry;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowEdge;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowGenerateRequest;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;
import com.vs.vsaiagent.workflowbuilder.template.AgentNodeTemplate;
import com.vs.vsaiagent.workflowbuilder.template.DifyNodeTemplate;
import com.vs.vsaiagent.workflowbuilder.template.DifyWorkflowTemplate;
import com.vs.vsaiagent.workflowbuilder.util.YamlUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow IR → Dify DSL YAML。
 *
 * 强约束：DSL 一律由 Java 模板（template 包）拼装，不允许大模型直接产出 YAML。
 *
 * 两种生成形态（见 {@link WorkflowGenerateRequest}）：
 *  - http（默认）：工具 → 独立 HTTP 请求节点，图固定编排、参数齐全、不依赖 Dify 插件；
 *  - agent：把所有工具折叠进一个 Agent 节点（挂载 MCP 工具），由 LLM 自主决定调用哪些。
 */
@Service
public class WorkflowDslGenerateService {

    private static final int START_X = 80;
    private static final int STEP_X = 300;
    private static final int BASE_Y = 280;
    private static final int ROW_GAP = 140;
    private static final int AGENT_ROW_GAP = 280;
    private static final String AGENT_NODE_ID = "agent";
    private static final String AGG_NODE_ID = "aggregate";

    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;

    private final String modelProvider;
    private final String modelName;
    private final String runtimeBaseUrl;

    // Agent 形态相关配置（默认值对齐本地 Dify：vs-agent MCP + langgenius/agent 策略）
    private final String mcpProviderName;
    private final String mcpProviderShowName;
    private final String agentStrategyProvider;
    private final String agentStrategyName;
    private final String agentStrategyLabel;
    private final String agentPluginUid;
    private final int agentMaxIterations;

    @Autowired
    public WorkflowDslGenerateService(
            ToolRegistry toolRegistry,
            SkillRegistry skillRegistry,
            @Value("${app.workflow-builder.model.provider:tongyi}") String modelProvider,
            @Value("${app.workflow-builder.model.name:qwen-max}") String modelName,
            @Value("${app.workflow-builder.runtime-base-url:http://localhost:8081/api}") String runtimeBaseUrl,
            @Value("${app.workflow-builder.mcp.provider-name:vs-agent}") String mcpProviderName,
            @Value("${app.workflow-builder.mcp.provider-show-name:vs-agent-tools}") String mcpProviderShowName,
            @Value("${app.workflow-builder.agent.strategy-provider:langgenius/agent/agent}") String agentStrategyProvider,
            @Value("${app.workflow-builder.agent.strategy-name:function_calling}") String agentStrategyName,
            @Value("${app.workflow-builder.agent.strategy-label:FunctionCalling}") String agentStrategyLabel,
            @Value("${app.workflow-builder.agent.plugin-uid:langgenius/agent:0.0.39@ceea6a76b3a495de2df2a55bcea82e903d49b06f4d8e1463c2aa085657f27164}") String agentPluginUid,
            @Value("${app.workflow-builder.agent.max-iterations:5}") int agentMaxIterations) {
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.runtimeBaseUrl = runtimeBaseUrl;
        this.mcpProviderName = mcpProviderName;
        this.mcpProviderShowName = mcpProviderShowName;
        this.agentStrategyProvider = agentStrategyProvider;
        this.agentStrategyName = agentStrategyName;
        this.agentStrategyLabel = agentStrategyLabel;
        this.agentPluginUid = agentPluginUid;
        this.agentMaxIterations = agentMaxIterations;
    }

    /**
     * 便捷构造器：仅 http 形态使用（不注入注册表，agent 形态需走 Spring 注入的完整构造器）。
     * 供单元测试与"无注册表"场景；agent 配置取默认值。
     */
    public WorkflowDslGenerateService(String modelProvider, String modelName, String runtimeBaseUrl) {
        this(null, null, modelProvider, modelName, runtimeBaseUrl,
                "vs-agent", "vs-agent-tools", "langgenius/agent/agent",
                "function_calling", "FunctionCalling",
                "langgenius/agent:0.0.39@ceea6a76b3a495de2df2a55bcea82e903d49b06f4d8e1463c2aa085657f27164", 5);
    }

    /** 默认 http 编排 + workflow（单轮）形态，兼容旧调用。 */
    public String toDslYaml(WorkflowIR ir) {
        return toDslYaml(ir, WorkflowGenerateRequest.MODE_HTTP, WorkflowGenerateRequest.APP_WORKFLOW);
    }

    /** 兼容旧调用：指定编排形态，应用形态默认 workflow（单轮）。 */
    public String toDslYaml(WorkflowIR ir, String mode) {
        return toDslYaml(ir, mode, WorkflowGenerateRequest.APP_WORKFLOW);
    }

    /**
     * @param mode    编排形态：http（HTTP 节点固定编排）/ agent（单 Agent 节点挂 MCP 工具）。
     * @param appKind 应用形态：chatflow（advanced-chat 多轮对话，answer 收尾 + 记忆）
     *                / workflow（单轮，end 收尾 + Start 输入变量）。两者正交。
     */
    public String toDslYaml(WorkflowIR ir, String mode, String appKind) {
        if (ir == null || ir.nodes() == null || ir.nodes().isEmpty()) {
            throw new IllegalArgumentException("WorkflowIR 不能为空");
        }
        boolean chatflow = WorkflowGenerateRequest.APP_CHATFLOW.equalsIgnoreCase(appKind);
        if (WorkflowGenerateRequest.MODE_AGENT.equalsIgnoreCase(mode)) {
            String agentYaml = tryToAgentDsl(ir, chatflow);
            if (agentYaml != null) {
                return agentYaml;
            }
            // 无可挂载工具时退回 http 编排（纯 LLM 任务，Agent 节点 tools 必填会非法）
        }
        return toHttpDsl(ir, chatflow);
    }

    /**
     * http 编排调度：≥2 个工具时走并行 fan-out + 汇聚节点（真并行 + 新节点类型），否则走串行链。
     */
    private String toHttpDsl(WorkflowIR ir, boolean chatflow) {
        List<WorkflowNode> tools = ir.nodes().stream()
                .filter(n -> WorkflowNode.TYPE_TOOL.equals(n.type()))
                .toList();
        if (tools.size() >= 2) {
            return toParallelHttpDsl(ir, chatflow, tools);
        }
        return toSerialHttpDsl(ir, chatflow);
    }

    /**
     * 串行 http 编排：start → [http tool] → llm → 收尾（0 或 1 个工具）。
     * 收尾节点随 appKind 而变：chatflow→answer（对话回复），workflow→end（输出变量）。
     */
    private String toSerialHttpDsl(WorkflowIR ir, boolean chatflow) {
        String inputRef = chatflow ? DifyNodeTemplate.CHATFLOW_INPUT_REF : DifyNodeTemplate.WORKFLOW_INPUT_REF;

        // IR 节点 id → DSL data.type（answer 节点按 appKind 落成 answer 或 end，供边的 sourceType/targetType 使用）
        Map<String, String> dslTypeById = new HashMap<>();
        for (WorkflowNode n : ir.nodes()) {
            dslTypeById.put(n.id(), switch (n.type()) {
                case WorkflowNode.TYPE_TOOL -> "http-request";
                case WorkflowNode.TYPE_ANSWER -> chatflow ? "answer" : "end";
                default -> n.type();
            });
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        String llmNodeId = ir.nodes().stream()
                .filter(n -> WorkflowNode.TYPE_LLM.equals(n.type()))
                .map(WorkflowNode::id)
                .findFirst()
                .orElse(WorkflowPlanningService.LLM_NODE_ID);

        int x = START_X;
        List<String> toolNodeIds = new ArrayList<>();
        for (WorkflowNode n : ir.nodes()) {
            switch (n.type()) {
                case WorkflowNode.TYPE_START -> nodes.add(DifyNodeTemplate.startNode(n, x, BASE_Y, !chatflow));
                case WorkflowNode.TYPE_TOOL -> {
                    nodes.add(DifyNodeTemplate.httpRequestNode(n, x, BASE_Y, runtimeBaseUrl, inputRef));
                    toolNodeIds.add(n.id());
                }
                // LLM 节点引用其前面所有工具节点的输出（支持多工具编排，而非只引用上一个）
                case WorkflowNode.TYPE_LLM ->
                        nodes.add(DifyNodeTemplate.llmNode(n, x, BASE_Y, modelProvider, modelName, toolNodeIds, inputRef, chatflow));
                case WorkflowNode.TYPE_ANSWER -> nodes.add(chatflow
                        ? DifyNodeTemplate.answerNode(n, x, BASE_Y, llmNodeId)
                        : DifyNodeTemplate.endNode(n, x, BASE_Y, llmNodeId));
                default -> throw new IllegalArgumentException("不支持的节点类型: " + n.type());
            }
            x += STEP_X;
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (WorkflowEdge e : ir.edges()) {
            edges.add(DifyNodeTemplate.edge(
                    e.source(), dslTypeById.getOrDefault(e.source(), "custom"),
                    e.target(), dslTypeById.getOrDefault(e.target(), "custom")));
        }

        Map<String, Object> root = DifyWorkflowTemplate.root(ir.name(), ir.description(), nodes, edges, chatflow);
        return YamlUtil.dump(root);
    }

    /**
     * 并行 http 编排：start 同时 fan-out 到各工具（彼此独立、并行执行），各工具 fan-in 到一个汇聚节点
     * （template-transform 拼接全部结果），再 → llm → 收尾。工具≥2 时启用。
     * 之所以能并行：当前工具节点参数只引用 {@code ${start}} / LLM 常量，互不依赖彼此输出，故无数据依赖、可并发。
     */
    private String toParallelHttpDsl(WorkflowIR ir, boolean chatflow, List<WorkflowNode> tools) {
        String inputRef = chatflow ? DifyNodeTemplate.CHATFLOW_INPUT_REF : DifyNodeTemplate.WORKFLOW_INPUT_REF;

        WorkflowNode startNode = ir.nodes().stream()
                .filter(n -> WorkflowNode.TYPE_START.equals(n.type()))
                .findFirst().orElse(WorkflowNode.start());
        WorkflowNode llmNode = ir.nodes().stream()
                .filter(n -> WorkflowNode.TYPE_LLM.equals(n.type()))
                .findFirst()
                .orElse(WorkflowNode.llm(WorkflowPlanningService.LLM_NODE_ID, ir.name(), ir.description()));
        WorkflowNode terminalNode = ir.nodes().stream()
                .filter(n -> WorkflowNode.TYPE_ANSWER.equals(n.type()))
                .findFirst().orElse(WorkflowNode.answer());
        String terminalType = chatflow ? "answer" : "end";

        List<Map<String, Object>> nodes = new ArrayList<>();
        int colX = START_X;
        nodes.add(DifyNodeTemplate.startNode(startNode, colX, BASE_Y, !chatflow));

        // 并行工具列：同一 x，y 上下错开
        colX += STEP_X;
        int n = tools.size();
        List<String[]> sources = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            WorkflowNode t = tools.get(i);
            int ty = BASE_Y + (int) Math.round((i - (n - 1) / 2.0) * ROW_GAP);
            nodes.add(DifyNodeTemplate.httpRequestNode(t, colX, ty, runtimeBaseUrl, inputRef));
            sources.add(new String[]{t.id(), t.title()});
        }

        // 汇聚 → llm → 收尾
        colX += STEP_X;
        nodes.add(DifyNodeTemplate.aggregatorNode(AGG_NODE_ID, colX, BASE_Y, "汇聚工具结果", sources, "body"));
        colX += STEP_X;
        nodes.add(DifyNodeTemplate.llmNodeAggregated(llmNode, colX, BASE_Y,
                modelProvider, modelName, AGG_NODE_ID, inputRef, chatflow));
        colX += STEP_X;
        nodes.add(chatflow
                ? DifyNodeTemplate.answerNode(terminalNode, colX, BASE_Y, llmNode.id())
                : DifyNodeTemplate.endNode(terminalNode, colX, BASE_Y, llmNode.id()));

        List<Map<String, Object>> edges = new ArrayList<>();
        for (WorkflowNode t : tools) {
            edges.add(DifyNodeTemplate.edge(startNode.id(), "start", t.id(), "http-request"));
            edges.add(DifyNodeTemplate.edge(t.id(), "http-request", AGG_NODE_ID, "template-transform"));
        }
        edges.add(DifyNodeTemplate.edge(AGG_NODE_ID, "template-transform", llmNode.id(), "llm"));
        edges.add(DifyNodeTemplate.edge(llmNode.id(), "llm", terminalNode.id(), terminalType));

        Map<String, Object> root = DifyWorkflowTemplate.root(ir.name(), ir.description(), nodes, edges, chatflow);
        return YamlUtil.dump(root);
    }

    /**
     * agent 编排：start → agent(挂载 MCP 工具) → 收尾（chatflow→answer / workflow→end）。
     * 复用 IR 里规划器已选出的工具集合，折叠进一个 Agent 节点；无工具则返回 null（交由上层回退）。
     */
    private String tryToAgentDsl(WorkflowIR ir, boolean chatflow) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (WorkflowNode n : ir.nodes()) {
            if (WorkflowNode.TYPE_TOOL.equals(n.type()) && n.toolRef() != null) {
                Map<String, Object> entry = toMcpToolEntry(n.toolRef());
                if (entry != null) {
                    tools.add(entry);
                }
            }
        }
        if (tools.isEmpty()) {
            return null;   // 纯 LLM 任务，Agent 节点 tools 必填会非法 → 交由上层回退 http
        }
        // 工具足够多（≥4）时拆成多个并行 Agent（每个 Agent 自主调度一组工具），否则单 Agent 挂全部工具
        if (tools.size() >= 4) {
            return toMultiAgentDsl(ir, chatflow, tools);
        }
        return toSingleAgentDsl(ir, chatflow, tools);
    }

    /** 单 Agent：start → agent(挂全部工具) → 收尾。工具较少时用，结构最简。 */
    private String toSingleAgentDsl(WorkflowIR ir, boolean chatflow, List<Map<String, Object>> tools) {
        WorkflowNode startNode = ir.nodes().stream()
                .filter(n -> WorkflowNode.TYPE_START.equals(n.type()))
                .findFirst()
                .orElse(WorkflowNode.start());

        int x = START_X;
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(DifyNodeTemplate.startNode(startNode, x, BASE_Y, !chatflow));
        x += STEP_X;

        String instruction = buildAgentInstruction(ir.description());
        String query = chatflow ? DifyNodeTemplate.CHATFLOW_INPUT_REF : DifyNodeTemplate.WORKFLOW_INPUT_REF;
        nodes.add(AgentNodeTemplate.agentNode(AGENT_NODE_ID, x, BASE_Y,
                agentStrategyProvider, agentStrategyName, agentStrategyLabel, agentPluginUid,
                modelProvider, modelName, ir.name(), instruction, query, tools, agentMaxIterations, chatflow));
        x += STEP_X;

        WorkflowNode terminalNode = WorkflowNode.answer();
        String terminalType = chatflow ? "answer" : "end";
        nodes.add(chatflow
                ? DifyNodeTemplate.answerNode(terminalNode, x, BASE_Y, AGENT_NODE_ID)
                : DifyNodeTemplate.endNode(terminalNode, x, BASE_Y, AGENT_NODE_ID));

        List<Map<String, Object>> edges = new ArrayList<>();
        edges.add(DifyNodeTemplate.edge(startNode.id(), "start", AGENT_NODE_ID, "agent"));
        edges.add(DifyNodeTemplate.edge(AGENT_NODE_ID, "agent", terminalNode.id(), terminalType));

        Map<String, Object> root = DifyWorkflowTemplate.root(ir.name(), ir.description(), nodes, edges, chatflow);
        return YamlUtil.dump(root);
    }

    /**
     * 多 Agent 并行：把工具均分给 2 个 Agent 节点，从 start 并行 fan-out，各 Agent 自主调度其工具子集，
     * 再 fan-in 到汇聚节点(template-transform 拼接两路 text)，最后综合 LLM → 收尾。
     * 这是 Dify 支持的「多智能体」编排：在单 Agent 的运行时自主性之外，额外引入图级并行与分工。
     */
    private String toMultiAgentDsl(WorkflowIR ir, boolean chatflow, List<Map<String, Object>> tools) {
        String inputRef = chatflow ? DifyNodeTemplate.CHATFLOW_INPUT_REF : DifyNodeTemplate.WORKFLOW_INPUT_REF;
        WorkflowNode startNode = ir.nodes().stream()
                .filter(n -> WorkflowNode.TYPE_START.equals(n.type()))
                .findFirst().orElse(WorkflowNode.start());
        WorkflowNode llmNode = ir.nodes().stream()
                .filter(n -> WorkflowNode.TYPE_LLM.equals(n.type()))
                .findFirst()
                .orElse(WorkflowNode.llm(WorkflowPlanningService.LLM_NODE_ID, ir.name(), ir.description()));
        WorkflowNode terminalNode = ir.nodes().stream()
                .filter(n -> WorkflowNode.TYPE_ANSWER.equals(n.type()))
                .findFirst().orElse(WorkflowNode.answer());
        String terminalType = chatflow ? "answer" : "end";

        String instruction = buildAgentInstruction(ir.description());
        String query = inputRef;

        // 工具均分两组（前一半 / 后一半），各给一个 Agent
        int half = (tools.size() + 1) / 2;
        List<List<Map<String, Object>>> groups = List.of(
                new ArrayList<>(tools.subList(0, half)),
                new ArrayList<>(tools.subList(half, tools.size())));

        List<Map<String, Object>> nodes = new ArrayList<>();
        int colX = START_X;
        nodes.add(DifyNodeTemplate.startNode(startNode, colX, BASE_Y, !chatflow));

        // 并行 Agent 列：同一 x，y 上下错开
        colX += STEP_X;
        int k = groups.size();
        List<String> agentIds = new ArrayList<>();
        List<String[]> aggSources = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            String aid = "agent_" + (i + 1);
            String label = "智能体 " + (i + 1);
            int ay = BASE_Y + (int) Math.round((i - (k - 1) / 2.0) * AGENT_ROW_GAP);
            nodes.add(AgentNodeTemplate.agentNode(aid, colX, ay,
                    agentStrategyProvider, agentStrategyName, agentStrategyLabel, agentPluginUid,
                    modelProvider, modelName, label, instruction, query, groups.get(i), agentMaxIterations, chatflow));
            agentIds.add(aid);
            aggSources.add(new String[]{aid, label});
        }

        // 汇聚（取各 Agent 的 text）→ 综合 LLM → 收尾
        colX += STEP_X;
        nodes.add(DifyNodeTemplate.aggregatorNode(AGG_NODE_ID, colX, BASE_Y, "汇聚智能体结果", aggSources, "text"));
        colX += STEP_X;
        nodes.add(DifyNodeTemplate.llmNodeAggregated(llmNode, colX, BASE_Y,
                modelProvider, modelName, AGG_NODE_ID, inputRef, chatflow));
        colX += STEP_X;
        nodes.add(chatflow
                ? DifyNodeTemplate.answerNode(terminalNode, colX, BASE_Y, llmNode.id())
                : DifyNodeTemplate.endNode(terminalNode, colX, BASE_Y, llmNode.id()));

        List<Map<String, Object>> edges = new ArrayList<>();
        for (String aid : agentIds) {
            edges.add(DifyNodeTemplate.edge(startNode.id(), "start", aid, "agent"));
            edges.add(DifyNodeTemplate.edge(aid, "agent", AGG_NODE_ID, "template-transform"));
        }
        edges.add(DifyNodeTemplate.edge(AGG_NODE_ID, "template-transform", llmNode.id(), "llm"));
        edges.add(DifyNodeTemplate.edge(llmNode.id(), "llm", terminalNode.id(), terminalType));

        Map<String, Object> root = DifyWorkflowTemplate.root(ir.name(), ir.description(), nodes, edges, chatflow);
        return YamlUtil.dump(root);
    }

    /**
     * 把 IR 的 toolRef（tool:xxx / skill:xxx）映射为一个挂载到 Agent 节点的 MCP 工具条目。
     * 描述与参数名取自后端注册表；注册表查不到时仍按工具名挂载（MCP 已暴露该名）。
     */
    private Map<String, Object> toMcpToolEntry(String toolRef) {
        String ref = toolRef.trim();
        String name;
        String description = "";
        List<String> params = new ArrayList<>();

        if (ref.startsWith("tool:")) {
            name = ref.substring("tool:".length());
            var meta = toolRegistry.findByName(name).map(t -> t.metadata());
            if (meta.isPresent()) {
                description = nonBlank(meta.get().getDescription(), "");
                if (meta.get().getRequiredParams() != null) {
                    params.addAll(meta.get().getRequiredParams());
                }
            }
        } else if (ref.startsWith("skill:")) {
            name = ref.substring("skill:".length());
            var skill = skillRegistry.find(name).map(Skill::metadata);
            if (skill.isPresent()) {
                description = nonBlank(skill.get().description(), "");
                if (skill.get().inputs() != null) {
                    for (SkillParam p : skill.get().inputs()) {
                        if (p.name() != null && !p.name().isBlank()) {
                            params.add(p.name());
                        }
                    }
                }
            }
        } else {
            return null;
        }

        return AgentNodeTemplate.mcpToolEntry(mcpProviderName, mcpProviderShowName, name, description, params);
    }

    private String buildAgentInstruction(String requirement) {
        String req = requirement == null ? "" : requirement.trim();
        // 关键：Agent 节点的工具参数由 Dify 运行时 LLM 自行填充，它不知道"今天"是哪天，
        // 不注入当前日期会导致 date 类参数被瞎编（曾出现错到 4 月 / 2025 年）。这里把生成时的当前日期
        // 写进指令，让运行时 LLM 有据可依（HTTP 模式则由后端规划器在生成时直接算好日期，无此问题）。
        return "你是一个可调用工具的智能助手。请根据用户输入，自主判断需要调用哪些已挂载的工具来完成任务，"
                + "按需多次调用并整合各工具结果，最终输出结构清晰、关键结论先行的中文回答，不要编造内容。\n\n"
                + "当前日期：" + LocalDate.now() + "。若用户未明确指定日期，涉及日期的工具参数（如 date）"
                + "一律基于当前日期推算：例如“今晚/今天”取当天，“周末”取最近的周六，且年份必须用当前年份，不要凭记忆臆造。\n\n"
                + "任务背景：" + req;
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
