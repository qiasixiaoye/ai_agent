package com.vs.vsaiagent.observability.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentRequestLogEntity {
    private Long id;
    private String requestId;
    private String traceId;
    private String sessionId;
    private String scene;
    private String userInput;
    private String modelName;
    private String finalOutput;
    private String status;
    private Long totalCostMs;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
