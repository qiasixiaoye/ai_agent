package com.vs.vsaiagent.workflowbuilder.model;

/**
 * 工作流本地试运行的单步结果。
 *
 * @param nodeId       节点 id
 * @param type         节点类型：start | tool | llm | answer
 * @param title        节点标题
 * @param success      该步是否成功
 * @param output       该步输出文本
 * @param errorMessage 失败时的错误信息（成功时为 null）
 * @param elapsedMs    该步耗时（毫秒）
 */
public record WorkflowStepResult(
        String nodeId,
        String type,
        String title,
        boolean success,
        String output,
        String errorMessage,
        long elapsedMs
) {
}
