package com.vs.vsaiagent.agentplatform.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ToolExecuteRequest {
    private String toolName;
    private Map<String, Object> arguments;
    private String traceId;
}
