package com.vs.vsaiagent.agentplatform.model;

import lombok.Data;

import java.util.List;

@Data
public class TaskExecuteRequest {
    private String traceId;
    private Integer maxSteps;
    private List<TaskStepDefinition> steps;
}
