package com.vs.vsaiagent.workflowbuilder.model;

/**
 * Workflow IR 边（有向）。
 *
 * @param source 源节点 id
 * @param target 目标节点 id
 */
public record WorkflowEdge(String source, String target) {
}
