package com.vs.vsaiagent.agentplatform.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class TaskStepDefinition {
    private String stepId;
    private String toolName;
    private Map<String, Object> args;
    private boolean required;
}
