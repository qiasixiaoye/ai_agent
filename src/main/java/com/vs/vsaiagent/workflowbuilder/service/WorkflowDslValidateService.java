package com.vs.vsaiagent.workflowbuilder.service;

import com.vs.vsaiagent.workflowbuilder.model.ValidateResult;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowEdge;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;
import com.vs.vsaiagent.workflowbuilder.util.GraphValidateUtil;
import com.vs.vsaiagent.workflowbuilder.util.YamlUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DSL / IR 基础结构校验。
 *
 * 校验项：
 *  1. 存在 start 节点
 *  2. 存在 answer 节点
 *  3. 节点 id 唯一
 *  4. 边 source/target 指向真实节点
 *  5. 无环
 *  6. （DSL 路径）YAML 可解析且包含 workflow.graph.nodes/edges
 *
 * DSL 节点类型兼容两种写法：节点顶层 type（非 custom 时）或 data.type（Dify 标准）。
 */
@Service
public class WorkflowDslValidateService {

    public ValidateResult validateIr(WorkflowIR ir) {
        if (ir == null || ir.nodes() == null) {
            return ValidateResult.fail(List.of("IR 为空"));
        }
        List<String[]> edges = new ArrayList<>();
        if (ir.edges() != null) {
            for (WorkflowEdge e : ir.edges()) {
                edges.add(new String[]{e.source(), e.target()});
            }
        }
        List<GraphNode> nodes = ir.nodes().stream()
                .map(n -> new GraphNode(n.id(), n.type()))
                .toList();
        return validateGraph(nodes, edges);
    }

    public ValidateResult validateDsl(String dslYaml) {
        Map<String, Object> root;
        try {
            root = YamlUtil.parse(dslYaml);
        } catch (IllegalArgumentException e) {
            return ValidateResult.fail(List.of(e.getMessage()));
        }

        Object workflow = root.get("workflow");
        if (!(workflow instanceof Map<?, ?> workflowMap)) {
            return ValidateResult.fail(List.of("缺少 workflow 配置块"));
        }
        Object graph = workflowMap.get("graph");
        if (!(graph instanceof Map<?, ?> graphMap)) {
            return ValidateResult.fail(List.of("缺少 workflow.graph 配置块"));
        }

        List<GraphNode> nodes = new ArrayList<>();
        if (graphMap.get("nodes") instanceof List<?> nodeList) {
            for (Object o : nodeList) {
                if (o instanceof Map<?, ?> nodeMap) {
                    nodes.add(new GraphNode(str(nodeMap.get("id")), resolveNodeType(nodeMap)));
                }
            }
        }
        List<String[]> edges = new ArrayList<>();
        if (graphMap.get("edges") instanceof List<?> edgeList) {
            for (Object o : edgeList) {
                if (o instanceof Map<?, ?> edgeMap) {
                    edges.add(new String[]{str(edgeMap.get("source")), str(edgeMap.get("target"))});
                }
            }
        }
        return validateGraph(nodes, edges);
    }

    private ValidateResult validateGraph(List<GraphNode> nodes, List<String[]> edges) {
        List<String> errors = new ArrayList<>();

        if (nodes.isEmpty()) {
            errors.add("graph.nodes 为空");
            return ValidateResult.fail(errors);
        }

        // 3. id 唯一
        Set<String> ids = new LinkedHashSet<>();
        Set<String> duplicated = new LinkedHashSet<>();
        for (GraphNode n : nodes) {
            if (n.id() == null || n.id().isBlank()) {
                errors.add("存在缺少 id 的节点");
                continue;
            }
            if (!ids.add(n.id())) {
                duplicated.add(n.id());
            }
        }
        duplicated.forEach(id -> errors.add("节点 id 重复: " + id));

        // 1/2. start / answer 存在
        Set<String> types = new HashSet<>();
        nodes.forEach(n -> types.add(n.type()));
        if (!types.contains(WorkflowNode.TYPE_START)) {
            errors.add("缺少 start 节点");
        }
        if (!types.contains(WorkflowNode.TYPE_ANSWER)) {
            errors.add("缺少 answer 节点");
        }

        // 4. 边引用存在
        for (String[] e : edges) {
            if (e[0] == null || !ids.contains(e[0])) {
                errors.add("边 source 指向不存在的节点: " + e[0]);
            }
            if (e[1] == null || !ids.contains(e[1])) {
                errors.add("边 target 指向不存在的节点: " + e[1]);
            }
        }

        // 5. 无环
        if (GraphValidateUtil.hasCycle(ids, edges)) {
            errors.add("工作流图中存在环");
        }

        return errors.isEmpty() ? ValidateResult.ok() : ValidateResult.fail(errors);
    }

    /** Dify DSL：业务类型在 data.type；顶层 type 为 custom。兼容简化 DSL 顶层直接写业务类型。 */
    private String resolveNodeType(Map<?, ?> nodeMap) {
        if (nodeMap.get("data") instanceof Map<?, ?> data) {
            String dataType = str(data.get("type"));
            if (dataType != null) {
                return dataType;
            }
        }
        String topType = str(nodeMap.get("type"));
        return "custom".equals(topType) ? null : topType;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private record GraphNode(String id, String type) {
    }
}
