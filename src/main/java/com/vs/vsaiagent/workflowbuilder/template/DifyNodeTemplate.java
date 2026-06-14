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

    private DifyNodeTemplate() {
    }

    public static Map<String, Object> startNode(WorkflowNode node, int x, int y) {
        Map<String, Object> variable = new LinkedHashMap<>();
        variable.put("variable", START_INPUT_VAR);
        variable.put("label", "输入内容");
        variable.put("type", "paragraph");
        variable.put("required", true);
        variable.put("max_length", 4000);
        variable.put("options", List.of());

        Map<String, Object> data = baseData(node, "start");
        data.put("variables", List.of(variable));
        return canvasNode(node.id(), data, x, y);
    }

    public static Map<String, Object> llmNode(WorkflowNode node, int x, int y,
                                              String modelProvider, String modelName) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("provider", modelProvider);
        model.put("name", modelName);
        model.put("mode", "chat");
        model.put("completion_params", new LinkedHashMap<>(Map.of("temperature", 0.7)));

        List<Map<String, Object>> promptTemplate = new ArrayList<>();
        promptTemplate.add(message("system", node.instruction() == null ? "" : node.instruction()));
        promptTemplate.add(message("user", "{{#start." + START_INPUT_VAR + "#}}"));

        Map<String, Object> data = baseData(node, "llm");
        data.put("model", model);
        data.put("prompt_template", promptTemplate);
        data.put("context", new LinkedHashMap<>(Map.of("enabled", false, "variable_selector", List.of())));
        data.put("vision", new LinkedHashMap<>(Map.of("enabled", false)));
        data.put("variables", List.of());
        return canvasNode(node.id(), data, x, y);
    }

    public static Map<String, Object> answerNode(WorkflowNode node, int x, int y, String llmNodeId) {
        Map<String, Object> data = baseData(node, "answer");
        data.put("answer", "{{#" + llmNodeId + ".text#}}");
        data.put("variables", List.of());
        return canvasNode(node.id(), data, x, y);
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

    private static Map<String, Object> message(String role, String text) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("text", text);
        return msg;
    }
}
