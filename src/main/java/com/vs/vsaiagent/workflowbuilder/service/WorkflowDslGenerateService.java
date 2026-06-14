package com.vs.vsaiagent.workflowbuilder.service;

import com.vs.vsaiagent.workflowbuilder.model.WorkflowEdge;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;
import com.vs.vsaiagent.workflowbuilder.template.DifyNodeTemplate;
import com.vs.vsaiagent.workflowbuilder.template.DifyWorkflowTemplate;
import com.vs.vsaiagent.workflowbuilder.util.YamlUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow IR → Dify DSL YAML。
 *
 * 强约束：DSL 一律由 Java 模板（template 包）拼装，不允许大模型直接产出 YAML。
 */
@Service
public class WorkflowDslGenerateService {

    private static final int START_X = 80;
    private static final int STEP_X = 300;
    private static final int BASE_Y = 280;

    private final String modelProvider;
    private final String modelName;

    public WorkflowDslGenerateService(
            @Value("${app.workflow-builder.model.provider:tongyi}") String modelProvider,
            @Value("${app.workflow-builder.model.name:qwen-max}") String modelName) {
        this.modelProvider = modelProvider;
        this.modelName = modelName;
    }

    public String toDslYaml(WorkflowIR ir) {
        if (ir == null || ir.nodes() == null || ir.nodes().isEmpty()) {
            throw new IllegalArgumentException("WorkflowIR 不能为空");
        }

        Map<String, String> nodeTypeById = new HashMap<>();
        for (WorkflowNode n : ir.nodes()) {
            nodeTypeById.put(n.id(), n.type());
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        String llmNodeId = ir.nodes().stream()
                .filter(n -> WorkflowNode.TYPE_LLM.equals(n.type()))
                .map(WorkflowNode::id)
                .findFirst()
                .orElse(WorkflowPlanningService.LLM_NODE_ID);

        int x = START_X;
        for (WorkflowNode n : ir.nodes()) {
            switch (n.type()) {
                case WorkflowNode.TYPE_START -> nodes.add(DifyNodeTemplate.startNode(n, x, BASE_Y));
                case WorkflowNode.TYPE_LLM ->
                        nodes.add(DifyNodeTemplate.llmNode(n, x, BASE_Y, modelProvider, modelName));
                case WorkflowNode.TYPE_ANSWER -> nodes.add(DifyNodeTemplate.answerNode(n, x, BASE_Y, llmNodeId));
                default -> throw new IllegalArgumentException("不支持的节点类型: " + n.type());
            }
            x += STEP_X;
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (WorkflowEdge e : ir.edges()) {
            edges.add(DifyNodeTemplate.edge(
                    e.source(), nodeTypeById.getOrDefault(e.source(), "custom"),
                    e.target(), nodeTypeById.getOrDefault(e.target(), "custom")));
        }

        Map<String, Object> root = DifyWorkflowTemplate.root(ir.name(), ir.description(), nodes, edges);
        return YamlUtil.dump(root);
    }
}
