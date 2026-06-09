package com.vs.vsaiagent.workflow;

/**
 * 工作流边。当前 MVP 沿用边顺序作为拓扑顺序的提示，但执行器仍按 nodes 顺序串行执行。
 * 后续可扩展为真正的 DAG 拓扑排序。
 */
public record WorkflowEdge(String from, String to) {}
