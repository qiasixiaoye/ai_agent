package com.vs.vsaiagent.workflowbuilder.model;

import java.util.List;

/**
 * 工作流本地试运行结果。
 *
 * @param steps       每个节点的执行结果（按 IR 节点顺序）
 * @param success     是否全部步骤成功
 * @param finalOutput 最终输出（answer 节点的 output）
 */
public record WorkflowRunResult(
        List<WorkflowStepResult> steps,
        boolean success,
        String finalOutput
) {
}
