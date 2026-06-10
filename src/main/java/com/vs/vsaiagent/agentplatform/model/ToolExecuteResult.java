package com.vs.vsaiagent.agentplatform.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolExecuteResult {
    private String toolName;
    private boolean success;
    private String output;
    private String errorMessage;
    private Long costMs;
}
