package com.vs.vsaiagent.workflow;

import java.util.Collections;
import java.util.List;

/**
 * 工作流执行的聚合结果。
 */
public record WorkflowResult(
        String workflowId,
        boolean success,
        String output,
        String errorMessage,
        long elapsedMs,
        List<StepResult> steps
) {
    public WorkflowResult {
        steps = steps == null ? Collections.emptyList() : List.copyOf(steps);
    }
}
