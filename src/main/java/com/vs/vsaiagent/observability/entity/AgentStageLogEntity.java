package com.vs.vsaiagent.observability.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentStageLogEntity {
    private Long id;
    private String requestId;
    private String traceId;
    private String sessionId;
    private String stageType;
    private String stageName;
    private String toolName;
    private String inputPayload;
    private String outputPayload;
    private Long costMs;
    private Boolean success;
    private String errorMessage;
    private LocalDateTime eventTime;
}
