package com.vs.vsaiagent.agentplatform.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ToolInvokeRequest {
    private String traceId;
    private String tag;
    private Map<String, Object> arguments;
}
