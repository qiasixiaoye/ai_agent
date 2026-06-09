package com.vs.vsaiagent.workflow.service;

import com.vs.vsaiagent.workflow.WorkflowDef;
import com.vs.vsaiagent.workflow.WorkflowNode;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把内部 WorkflowDef 翻译成 Dify-style YAML DSL。
 *
 * 注意：Dify 不同版本的 DSL schema 细节略有差异；这里采用"通用最小骨架"，
 * 包含 app 元信息 + workflow.graph.nodes/edges。导入到具体 Dify 实例时
 * 可能需要少量手工补字段（如 model provider）。
 *
 * 节点映射：
 *   - llm   → Dify llm 节点
 *   - skill → Dify tool 节点（指向 /skills/{name}/execute，依赖反向 OpenAPI 导入）
 *   - start / end 节点自动补齐
 */
@Component
public class DifyDslExporter {

    public String toYaml(WorkflowDef def) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("app", appBlock(def));
        root.put("kind", "app");
        root.put("version", "0.1.0");

        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("graph", buildGraph(def));
        workflow.put("features", Map.of());
        root.put("workflow", workflow);

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);
        opts.setPrettyFlow(true);
        return new Yaml(opts).dump(root);
    }

    private Map<String, Object> appBlock(WorkflowDef def) {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", def.name() == null ? "untitled" : def.name());
        app.put("description", def.description() == null ? "" : def.description());
        app.put("icon", "🤖");
        app.put("icon_background", "#FFEAD5");
        app.put("mode", "workflow");
        return app;
    }

    private Map<String, Object> buildGraph(WorkflowDef def) {
        Map<String, Object> graph = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // start
        nodes.add(node("start", "start", Map.of("title", "Start", "variables", List.of(
                Map.of("variable", "input", "type", "string", "label", "input", "required", true)
        ))));

        String prev = "start";
        int x = 150;
        for (WorkflowNode n : def.nodes()) {
            x += 200;
            Map<String, Object> data = new LinkedHashMap<>();
            if ("llm".equalsIgnoreCase(n.type())) {
                data.put("title", "LLM " + n.id());
                data.put("prompt_template", List.of(
                        Map.of("role", "user", "text", n.prompt() == null ? "" : n.prompt())));
                data.put("output_variable", n.outputVar());
                nodes.add(node(n.id(), "llm", data));
            } else if ("skill".equalsIgnoreCase(n.type())) {
                data.put("title", "Skill " + n.skillName());
                data.put("tool_provider", "vs-ai-agent");
                data.put("tool_name", n.skillName());
                data.put("tool_parameters", n.args());
                data.put("output_variable", n.outputVar());
                nodes.add(node(n.id(), "tool", data));
            } else {
                continue;
            }
            edges.add(edge(prev, n.id()));
            prev = n.id();
        }
        // end
        Map<String, Object> endData = new LinkedHashMap<>();
        endData.put("title", "End");
        endData.put("outputs", List.of(
                Map.of("variable", def.outputVar() == null ? "output" : def.outputVar())));
        nodes.add(node("end", "end", endData));
        edges.add(edge(prev, "end"));

        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return graph;
    }

    private Map<String, Object> node(String id, String type, Map<String, Object> data) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("type", type);
        n.put("data", data);
        return n;
    }

    private Map<String, Object> edge(String from, String to) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("source", from);
        e.put("target", to);
        return e;
    }
}
