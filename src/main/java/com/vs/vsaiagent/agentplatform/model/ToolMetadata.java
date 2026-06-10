package com.vs.vsaiagent.agentplatform.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ToolMetadata {
    private String toolName;
    private String displayName;
    private String description;
    private ToolSourceType sourceType;
    private List<String> tags;
    private List<String> requiredParams;
    private Long timeoutMs;
}
