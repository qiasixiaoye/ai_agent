package com.vs.vsaiagent.workflowbuilder.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify「Agent 节点」模板：把若干工具挂到一个 Agent 节点上，由 LLM 自主决定调用哪些
 * （区别于 {@link DifyNodeTemplate#httpRequestNode} 的"图固定 HTTP 调用"模式）。
 *
 * 结构严格对齐本地 Dify 1.14.2 真实导出的 agent 节点（langgenius/agent 策略插件）：
 *  - data.type = agent；agent_strategy_provider_name = langgenius/agent/agent；
 *    agent_strategy_name = ReAct | function_calling；tool_node_version = "2"。
 *  - agent_parameters: model / tools / instruction / query 四个入参，统一 {type, value} 包装。
 *  - 工具条目 type=mcp 时，provider_name 取 MCP provider 的 server_identifier（按它解析，非 uuid），
 *    parameters 每个参数 {auto:1, value:null} 表示交由 LLM 自动填充。
 *
 * 这些字段若与目标 Dify 版本不符会导致导入后"节点异常/模型加载失败"，调整前请比对真实导出 DSL。
 */
public final class AgentNodeTemplate {

    private AgentNodeTemplate() {
    }

    /**
     * 构造一个 Agent 画布节点。
     *
     * @param tools 工具条目列表（用 {@link #mcpToolEntry} 构造）
     */
    /**
     * @param withMemory true（chatflow 多轮）→ Agent 开启会话记忆，跨轮携带历史；
     *                   false（workflow 单轮）→ 无记忆。
     */
    public static Map<String, Object> agentNode(String id, int x, int y,
                                                String strategyProvider, String strategyName,
                                                String strategyLabel, String pluginUid,
                                                String modelProvider, String modelName,
                                                String title, String instruction, String query,
                                                List<Map<String, Object>> tools, int maxIterations,
                                                boolean withMemory) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("provider", modelProvider);
        model.put("name", modelName);
        model.put("mode", "chat");
        model.put("completion_params", new LinkedHashMap<>(Map.of("temperature", 0.7)));
        model.put("type", "model-selector");
        model.put("model_type", "llm");
        model.put("model", modelName);

        Map<String, Object> agentParameters = new LinkedHashMap<>();
        agentParameters.put("instruction", constant(instruction));
        agentParameters.put("model", constant(model));
        agentParameters.put("query", constant(query));
        agentParameters.put("tools", constant(tools));
        agentParameters.put("maximum_iterations", constant(maxIterations));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "agent");
        data.put("title", title == null || title.isBlank() ? "智能体" : title);
        data.put("desc", "");
        data.put("selected", false);
        data.put("agent_strategy_provider_name", strategyProvider);
        data.put("agent_strategy_name", strategyName);
        data.put("agent_strategy_label", strategyLabel);
        data.put("tool_node_version", "2");
        data.put("plugin_unique_identifier", pluginUid);
        data.put("meta", new LinkedHashMap<>(Map.of(
                "minimum_dify_version", "1.7.0",
                "version", "0.0.2")));
        data.put("output_schema", new LinkedHashMap<>());
        data.put("context", new LinkedHashMap<>(Map.of("enabled", false, "variable_pool", List.of())));
        data.put("memory", memory(withMemory));
        data.put("vision", new LinkedHashMap<>(Map.of("enabled", false)));
        data.put("structured_output", new LinkedHashMap<>(Map.of("enabled", false)));
        data.put("agent_parameters", agentParameters);

        return canvasNode(id, data, x, y);
    }

    /**
     * 构造一个挂载到 Agent 节点的 MCP 工具条目。
     *
     * @param providerName     MCP provider 的 server_identifier（Dify 按它解析 provider）
     * @param providerShowName MCP provider 展示名
     * @param toolName         工具名（与 MCP 暴露的一致）
     * @param description      工具描述（写入 extra.description / tool_description，供 LLM 判断何时调用）
     * @param paramNames       工具参数名；每个写成 {auto:1, value:null}，交由 LLM 自动填充
     */
    public static Map<String, Object> mcpToolEntry(String providerName, String providerShowName,
                                                   String toolName, String description,
                                                   List<String> paramNames) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (paramNames != null) {
            for (String p : paramNames) {
                if (p != null && !p.isBlank()) {
                    Map<String, Object> param = new LinkedHashMap<>();
                    param.put("auto", 1);
                    param.put("value", null);
                    parameters.put(p, param);
                }
            }
        }

        String desc = description == null ? "" : description;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("enabled", true);
        entry.put("type", "mcp");
        entry.put("provider_name", providerName);
        entry.put("provider_show_name", providerShowName);
        entry.put("tool_name", toolName);
        entry.put("tool_label", toolName);
        entry.put("tool_description", desc);
        entry.put("settings", new LinkedHashMap<>());
        entry.put("parameters", parameters);
        entry.put("extra", new LinkedHashMap<>(Map.of("description", desc)));
        return entry;
    }

    private static Map<String, Object> constant(Object value) {
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("type", "constant");
        wrap.put("value", value);
        return wrap;
    }

    private static Map<String, Object> memory(boolean enabled) {
        Map<String, Object> rolePrefix = new LinkedHashMap<>();
        rolePrefix.put("assistant", "");
        rolePrefix.put("user", "");

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("enabled", enabled);
        window.put("size", enabled ? 10 : 100);

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("enabled", enabled);
        memory.put("query_prompt_template", "{{#sys.query#}}\n\n{{#sys.files#}}");
        memory.put("role_prefix", rolePrefix);
        memory.put("window", window);
        return memory;
    }

    private static Map<String, Object> canvasNode(String id, Map<String, Object> data, int x, int y) {
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("x", x);
        position.put("y", y);

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", "custom");
        node.put("data", data);
        node.put("position", position);
        node.put("positionAbsolute", new LinkedHashMap<>(position));
        node.put("sourcePosition", "right");
        node.put("targetPosition", "left");
        node.put("width", 244);
        node.put("height", 187);
        node.put("selected", false);
        return node;
    }
}
