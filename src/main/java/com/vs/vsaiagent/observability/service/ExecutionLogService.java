package com.vs.vsaiagent.observability.service;

import com.vs.vsaiagent.observability.entity.AgentRequestLogEntity;
import com.vs.vsaiagent.observability.entity.AgentStageLogEntity;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.vo.RequestTraceVO;

import java.time.LocalDateTime;
import java.util.List;

public interface ExecutionLogService {
    String startRequest(String scene, String sessionId, String userInput, String modelName);

    void logStage(String requestId, ExecutionStageType stageType, String stageName, String toolName,
                  String inputPayload, String outputPayload, Long costMs, boolean success, String errorMessage);

    void finishSuccess(String requestId, String finalOutput, long totalCostMs);

    void finishFail(String requestId, String errorMessage, long totalCostMs);

    RequestTraceVO queryTrace(String requestId);

    List<AgentRequestLogEntity> queryBySession(String sessionId, int limit);

    List<AgentRequestLogEntity> queryFailures(LocalDateTime start, LocalDateTime end, int limit);

    List<AgentStageLogEntity> queryStages(String requestId);
}
