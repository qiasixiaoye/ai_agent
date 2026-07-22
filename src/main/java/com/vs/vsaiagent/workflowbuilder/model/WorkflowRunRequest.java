package com.vs.vsaiagent.workflowbuilder.model;

/**
 * 本地试运行请求：直接携带 /generate 返回的 IR，避免引入额外的持久化/查询。
 *
 * @param ir    待执行的 Workflow IR
 * @param input 起始输入（对应 start 节点）
 */
public record WorkflowRunRequest(WorkflowIR ir, String input) {
}
