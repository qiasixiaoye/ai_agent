package com.vs.vsaiagent.agentplatform.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
public class TaskExecutionContext {
    private String traceId;
    private Integer maxSteps;
    private Integer currentStep;
    private LocalDateTime startedAt;
    private Map<String, ToolExecuteResult> stepResults;
    private Map<String, Object> attributes;

    public static TaskExecutionContext create(String traceId, Integer maxSteps) {
        return TaskExecutionContext.builder()
                .traceId(traceId)
                .maxSteps(maxSteps)
                .currentStep(0)
                .startedAt(LocalDateTime.now())
                .stepResults(new LinkedHashMap<>())
                .attributes(new LinkedHashMap<>())
                .build();
    }
}
