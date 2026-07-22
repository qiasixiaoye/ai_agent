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
 * @param warnings     诊断 / 告警（规划器选择、LLM 回退原因、形态降级等），供错误分析审计观测
 * @param requestId    observability 审计请求 id（诊断已落库，可据此回查）
 */
public record WorkflowGenerateResponse(
        String workflowId,
        String workflowName,
        WorkflowIR ir,
        String dslYaml,
        boolean valid,
        List<String> errors,
        List<String> warnings,
        String requestId
) {
}
