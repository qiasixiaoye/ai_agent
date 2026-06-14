package com.vs.vsaiagent.workflowbuilder.model;

import java.util.List;

/**
 * 生成工作流响应。
 *
 * @param workflowId   工作流 id，可用于 GET /workflow-builder/export/{workflowId}
 * @param workflowName 工作流名称
 * @param ir           中间表示
 * @param dslYaml      生成的 Dify DSL YAML
 * @param valid        校验是否通过
 * @param errors       校验错误列表
 */
public record WorkflowGenerateResponse(
        String workflowId,
        String workflowName,
        WorkflowIR ir,
        String dslYaml,
        boolean valid,
        List<String> errors
) {
}
