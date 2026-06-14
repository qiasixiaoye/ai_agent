package com.vs.vsaiagent.workflowbuilder.model;

/**
 * 生成工作流请求。
 *
 * @param requirement 用户自然语言需求
 */
public record WorkflowGenerateRequest(String requirement) {
}
