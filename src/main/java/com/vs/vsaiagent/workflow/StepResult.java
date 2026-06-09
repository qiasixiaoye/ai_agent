package com.vs.vsaiagent.workflow;

/**
 * 单个节点的执行结果。
 */
public record StepResult(
        String nodeId,
        String type,
        boolean success,
        String input,
        String output,
        String errorMessage,
        long elapsedMs
) {}
