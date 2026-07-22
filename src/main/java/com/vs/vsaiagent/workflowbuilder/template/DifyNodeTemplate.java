package com.vs.vsaiagent.workflowbuilder.template;

import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify 节点级模板：把 IR 节点翻译成 Dify graph.nodes 元素。
 *
 * 结构参考 Dify 0.x 导出的 workflow DSL：
 *  - 画布节点统一 type: custom，业务类型放在 data.type
 *  - start 节点带 variables 定义（这里固定一个 paragraph 类型的 input 变量）
 *  - llm 节点带 model + prompt_template，user 消息引用 {{#start.input#}}
 *  - answer 节点引用 {{#<llmNodeId>.text#}}
 *
 * 注意：不同 Dify 版本字段略有差异，导入失败时优先比对本地 Dify 导出的真实 DSL 调整此处模板。
 */
public final class DifyNodeTemplate {

    public static final String START_INPUT_VAR = "input";

    /** workflow（单轮）：用户输入来自 Start 节点的 input 变量。 */
    public static final String WORKFLOW_INPUT_REF = "{{#start." + START_INPUT_VAR + "#}}";
    /** advanced-chat（多轮对话）：用户输入来自对话框系统变量 sys.query。 */
    public static final String CHATFLOW_INPUT_REF = "{{#sys.query#}}";

    private DifyNodeTemplate() {
    }

    /** 兼容旧调用：带 input 输入变量（workflow 单轮形态）。 */
    public static Map<String, Object> startNode(WorkflowNode node, int x, int y) {
        return startNode(node, x, y, true);
    }

    /**
     * @param withInputVar true → Start 声明一个必填 input 变量（workflow 单轮，跑前先填表单）；
     *                     false → Start 无用户变量（chatflow 多轮，输入走对话框 sys.query）。
     */
    public static Map<String, Object> startNode(WorkflowNode node, int x, int y, boolean withInputVar) {
        Map<String, Object> data = baseData(node, "start");
        if (withInputVar) {
            Map<String, Object> variable = new LinkedHashMap<>();
            variable.put("variable", START_INPUT_VAR);
            variable.put("label", "输入内容");
            variable.put("type", "paragraph");
            variable.put("required", true);
            variable.put("max_length", 4000);
            variable.put("options", List.of());
            data.put("variables", List.of(variable));
        } else {
            data.put("variables", List.of());
        }
        return canvasNode(node.id(), data, x, y);
    }

    public static Map<String, Object> llmNode(WorkflowNode node, int x, int y,
                                              String modelProvider, String modelName) {
        return llmNode(node, x, y, modelProvider, modelName, List.of());
    }

    public static Map<String, Object> llmNode(WorkflowNode node, int x, int y,
                                              String modelProvider, String modelName, String toolNodeId) {
        return llmNode(node, x, y, modelProvider, modelName,
                toolNodeId == null || toolNodeId.isBlank() ? List.of() : List.of(toolNodeId));
    }

    public static Map<String, Object> llmNode(WorkflowNode node, int x, int y,
                                              String modelProvider, String modelName, List<String> toolNodeIds) {
        return llmNode(node, x, y, modelProvider, modelName, toolNodeIds, WORKFLOW_INPUT_REF, false);
    }

    /**
     * @param toolNodeIds 该 LLM 节点之前所有 tool 节点的 id（按顺序）。user 消息会附带每个节点的
     *                    HTTP 响应体（{{#&lt;id&gt;.body#}}），从而支持多工具结果汇总；为空则只引用起始输入。
     * @param inputRef    用户输入引用：workflow 用 {@link #WORKFLOW_INPUT_REF}，chatflow 用 {@link #CHATFLOW_INPUT_REF}。
     * @param withMemory  true（chatflow 多轮）→ 挂载会话记忆，模型携带历史对话；false（workflow 单轮）→ 无记忆。
     */
    public static Map<String, Object> llmNode(WorkflowNode node, int x, int y,
                                              String modelProvider, String modelName, List<String> toolNodeIds,
                                              String inputRef, boolean withMemory) {
        StringBuilder userMessage = new StringBuilder(inputRef);
        if (toolNodeIds != null && !toolNodeIds.isEmpty()) {
            int i = 1;
            for (String id : toolNodeIds) {
                userMessage.append("\n\n工具").append(i++).append("执行结果：{{#").append(id).append(".body#}}");
            }
        }
        return llmNodeImpl(node, x, y, modelProvider, modelName, userMessage.toString(), withMemory);
    }

    /**
     * 并行 fan-in 版 LLM 节点：多路工具结果已被汇聚节点拼成一个变量，user 消息单点引用 {{#&lt;aggId&gt;.output#}}。
     */
    public static Map<String, Object> llmNodeAggregated(WorkflowNode node, int x, int y,
                                                        String modelProvider, String modelName,
                                                        String aggregatorNodeId, String inputRef, boolean withMemory) {
        String userMessage = inputRef
                + "\n\n以下是各工具/技能并行执行后的结果汇总，请基于它们作答：\n{{#" + aggregatorNodeId + ".output#}}";
        return llmNodeImpl(node, x, y, modelProvider, modelName, userMessage, withMemory);
    }

    private static Map<String, Object> llmNodeImpl(WorkflowNode node, int x, int y,
                                                   String modelProvider, String modelName,
                                                   String userMessage, boolean withMemory) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("provider", modelProvider);
        model.put("name", modelName);
        model.put("mode", "chat");
        model.put("completion_params", new LinkedHashMap<>(Map.of("temperature", 0.7)));

        List<Map<String, Object>> promptTemplate = new ArrayList<>();
        promptTemplate.add(message("system", node.instruction() == null ? "" : node.instruction()));
        promptTemplate.add(message("user", userMessage));

        Map<String, Object> data = baseData(node, "llm");
        data.put("model", model);
        data.put("prompt_template", promptTemplate);
        data.put("context", new LinkedHashMap<>(Map.of("enabled", false, "variable_selector", List.of())));
        data.put("vision", new LinkedHashMap<>(Map.of("enabled", false)));
        if (withMemory) {
            data.put("memory", chatMemory());
        }
        data.put("variables", List.of());
        return canvasNode(node.id(), data, x, y);
    }

    /**
     * tool 节点 → Dify HTTP Request 节点。toolRef 形如
     * {@code tool:<toolName>}（调用 /agent-platform/tools/{name}/execute，
     * body 包了一层 "arguments"）或 {@code skill:<skillName>}（调用
     * /skills/{name}/execute，body 即参数本身）。
     * instruction 中的 {@code ${start}} 占位符会被替换为
     * Dify 变量 {@code {{#start.input#}}}。
     */
    /** 兼容旧调用：input 占位符替换为 workflow 的 start.input。 */
    public static Map<String, Object> httpRequestNode(WorkflowNode node, int x, int y, String baseUrl) {
        return httpRequestNode(node, x, y, baseUrl, WORKFLOW_INPUT_REF);
    }

    /**
     * @param inputRef 参数模板里 {@code ${start}} 占位符替换成的 Dify 变量引用：
     *                 workflow 用 {@link #WORKFLOW_INPUT_REF}，chatflow 用 {@link #CHATFLOW_INPUT_REF}。
     */
    public static Map<String, Object> httpRequestNode(WorkflowNode node, int x, int y, String baseUrl, String inputRef) {
        String toolRef = node.toolRef() == null ? "" : node.toolRef();
        String argsJson = renderArgsForDify(node.instruction(), inputRef);

        String url;
        String bodyJson;
        if (toolRef.startsWith("tool:")) {
            String name = toolRef.substring("tool:".length());
            url = baseUrl + "/agent-platform/tools/" + name + "/execute";
            bodyJson = "{\"arguments\": " + argsJson + "}";
        } else if (toolRef.startsWith("skill:")) {
            String name = toolRef.substring("skill:".length());
            url = baseUrl + "/skills/" + name + "/execute";
            bodyJson = argsJson;
        } else {
            throw new IllegalArgumentException("不支持的 toolRef: " + toolRef);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "json");
        body.put("data", bodyJson);

        Map<String, Object> data = baseData(node, "http-request");
        data.put("method", "post");
        data.put("url", url);
        data.put("headers", "Content-Type:application/json");
        data.put("params", "");
        data.put("body", body);
        data.put("authorization", new LinkedHashMap<>(Map.of("type", "no-auth")));
        data.put("variables", List.of());
        return canvasNode(node.id(), data, x, y);
    }

    private static String renderArgsForDify(String argsTemplate, String inputRef) {
        if (argsTemplate == null || argsTemplate.isBlank()) {
            return "{}";
        }
        return argsTemplate.replace("${start}", inputRef);
    }

    /** answer 节点（advanced-chat / Chatflow 收尾）：把上游节点的 text 直接作为对话回复。 */
    public static Map<String, Object> answerNode(WorkflowNode node, int x, int y, String sourceNodeId) {
        Map<String, Object> data = baseData(node, "answer");
        data.put("answer", "{{#" + sourceNodeId + ".text#}}");
        data.put("variables", List.of());
        return canvasNode(node.id(), data, x, y);
    }

    /**
     * end 节点（workflow 收尾）：把上游节点的 text 暴露为工作流输出变量 {@code text}。
     * （workflow 模式没有 answer 节点，终点必须是 end。）
     */
    public static Map<String, Object> endNode(WorkflowNode node, int x, int y, String sourceNodeId) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("variable", "text");
        output.put("value_selector", List.of(sourceNodeId, "text"));

        Map<String, Object> data = baseData(node, "end");
        data.put("outputs", List.of(output));
        return canvasNode(node.id(), data, x, y);
    }

    /**
     * 汇聚节点（template-transform）：把多个并行分支的 body 输出按 Jinja2 模板拼成一个字符串 output，
     * 供下游 LLM 单点引用 {{#&lt;id&gt;.output#}}。
     *
     * <p>为何用 template-transform 而非 variable-aggregator：后者是「取首个非空」语义（适合 if-else /
     * question-classifier 的分支择一），并行全跑场景下会丢掉除第一路外的所有结果；template-transform
     * 才能把全部分支结果拼接保留。
     *
     * @param sources 有序 [nodeId, 展示名]，每个分支取其 {@code field} 输出
     * @param field   各分支取哪个输出字段：http 请求节点取 {@code body}，Agent 节点取 {@code text}
     */
    public static Map<String, Object> aggregatorNode(String id, int x, int y, String title,
                                                     List<String[]> sources, String field) {
        List<Map<String, Object>> variables = new ArrayList<>();
        StringBuilder tpl = new StringBuilder();
        int i = 1;
        for (String[] s : sources) {
            String var = "r" + i;
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("variable", var);
            v.put("value_selector", List.of(s[0], field));
            variables.add(v);
            String label = (s.length > 1 && s[1] != null && !s[1].isBlank()) ? s[1] : ("工具" + i);
            tpl.append("【").append(label).append("】\n{{ ").append(var).append(" }}\n\n");
            i++;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "template-transform");
        data.put("title", title == null || title.isBlank() ? "汇聚工具结果" : title);
        data.put("desc", "");
        data.put("selected", false);
        data.put("variables", variables);
        data.put("template", tpl.toString().trim());
        return canvasNode(id, data, x, y);
    }

    public static Map<String, Object> edge(String source, String sourceType, String target, String targetType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceType", sourceType);
        data.put("targetType", targetType);
        data.put("isInIteration", false);

        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", source + "-source-" + target + "-target");
        edge.put("source", source);
        edge.put("sourceHandle", "source");
        edge.put("target", target);
        edge.put("targetHandle", "target");
        edge.put("type", "custom");
        edge.put("zIndex", 0);
        edge.put("data", data);
        return edge;
    }

    private static Map<String, Object> baseData(WorkflowNode node, String difyType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", difyType);
        data.put("title", node.title() == null ? difyType : node.title());
        data.put("desc", "");
        data.put("selected", false);
        return data;
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
        node.put("height", 98);
        node.put("selected", false);
        return node;
    }

    /** LLM 节点会话记忆（chatflow 多轮）：window 开启 → 模型携带最近若干轮对话历史。 */
    private static Map<String, Object> chatMemory() {
        Map<String, Object> rolePrefix = new LinkedHashMap<>();
        rolePrefix.put("user", "");
        rolePrefix.put("assistant", "");

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("enabled", true);
        window.put("size", 10);

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("role_prefix", rolePrefix);
        memory.put("window", window);
        memory.put("query_prompt_template", "{{#sys.query#}}");
        return memory;
    }

    private static Map<String, Object> message(String role, String text) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("text", text);
        return msg;
    }
}
