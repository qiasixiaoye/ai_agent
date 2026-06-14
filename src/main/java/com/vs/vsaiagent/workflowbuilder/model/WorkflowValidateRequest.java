package com.vs.vsaiagent.workflowbuilder.model;

/**
 * 校验工作流请求。
 *
 * @param dslYaml Dify DSL YAML 文本
 */
public record WorkflowValidateRequest(String dslYaml) {
}
