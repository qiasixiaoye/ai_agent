package com.vs.vsaiagent.agentplatform.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TaskExecuteResult {
    private String traceId;
    private boolean success;
    private Integer executedSteps;
    private String summary;
    private List<ToolExecuteResult> results;
}
