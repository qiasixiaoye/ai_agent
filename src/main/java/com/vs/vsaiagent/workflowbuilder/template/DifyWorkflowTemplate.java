package com.vs.vsaiagent.workflowbuilder.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify 工作流级模板：app 元信息 + workflow 骨架（features / graph 容器）。
 */
public final class DifyWorkflowTemplate {

    /** 与本地 Dify 实例版本对齐时按需调整。 */
    public static final String DSL_VERSION = "0.1.5";

    private DifyWorkflowTemplate() {
    }

    /** 兼容旧调用：默认 workflow（单轮）形态。 */
    public static Map<String, Object> root(String name, String description,
                                           List<Map<String, Object>> nodes,
                                           List<Map<String, Object>> edges) {
        return root(name, description, nodes, edges, false);
    }

    /**
     * @param chatflow true → advanced-chat（Chatflow，多轮对话，answer 收尾）；
     *                 false → workflow（单轮，end 收尾）。
     *                 这是 Dify 图编排仅有的两种 app.mode；agent 是节点类型，不是 app.mode。
     */
    public static Map<String, Object> root(String name, String description,
                                           List<Map<String, Object>> nodes,
                                           List<Map<String, Object>> edges,
                                           boolean chatflow) {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", name == null ? "untitled" : name);
        app.put("description", description == null ? "" : description);
        app.put("icon", "🤖");
        app.put("icon_background", "#FFEAD5");
        app.put("mode", chatflow ? "advanced-chat" : "workflow");
        app.put("use_icon_as_answer_icon", false);

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);

        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("conversation_variables", List.of());
        workflow.put("environment_variables", List.of());
        workflow.put("features", features());
        workflow.put("graph", graph);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("app", app);
        root.put("kind", "app");
        root.put("version", DSL_VERSION);
        root.put("workflow", workflow);
        return root;
    }

    private static Map<String, Object> features() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("file_upload", disabled());
        features.put("opening_statement", "");
        features.put("retriever_resource", disabled());
        features.put("sensitive_word_avoidance", disabled());
        features.put("speech_to_text", disabled());
        features.put("suggested_questions", List.of());
        features.put("suggested_questions_after_answer", disabled());
        features.put("text_to_speech", disabled());
        return features;
    }

    private static Map<String, Object> disabled() {
        return new LinkedHashMap<>(Map.of("enabled", false));
    }
}
