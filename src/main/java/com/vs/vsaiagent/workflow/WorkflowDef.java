package com.vs.vsaiagent.workflow;

import java.util.Collections;
import java.util.List;

/**
 * 工作流定义。由 WorkflowGenerator 从自然语言生成，由 WorkflowExecutor 执行。
 *
 * outputVar：整个工作流"最终输出"对应的变量名；不指定时取最后一个节点的 outputVar。
 */
public record WorkflowDef(
        String id,
        String name,
        String description,
        List<WorkflowNode> nodes,
        List<WorkflowEdge> edges,
        String outputVar
) {
    public WorkflowDef {
        nodes = nodes == null ? Collections.emptyList() : List.copyOf(nodes);
        edges = edges == null ? Collections.emptyList() : List.copyOf(edges);
    }
}
