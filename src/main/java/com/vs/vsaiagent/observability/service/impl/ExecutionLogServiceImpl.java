package com.vs.vsaiagent.observability.service.impl;

import cn.hutool.core.util.StrUtil;
import com.vs.vsaiagent.observability.context.TraceContext;
import com.vs.vsaiagent.observability.context.TraceInfo;
import com.vs.vsaiagent.observability.entity.AgentRequestLogEntity;
import com.vs.vsaiagent.observability.entity.AgentStageLogEntity;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.enums.ExecutionStatus;
import com.vs.vsaiagent.observability.repository.AgentRequestLogRepository;
import com.vs.vsaiagent.observability.repository.AgentStageLogRepository;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import com.vs.vsaiagent.observability.vo.RequestTraceVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ExecutionLogServiceImpl implements ExecutionLogService {

    private final AgentRequestLogRepository requestLogRepository;
    private final AgentStageLogRepository stageLogRepository;

    public ExecutionLogServiceImpl(AgentRequestLogRepository requestLogRepository,
                                   AgentStageLogRepository stageLogRepository) {
        this.requestLogRepository = requestLogRepository;
        this.stageLogRepository = stageLogRepository;
    }

    @Override
    public String startRequest(String scene, String sessionId, String userInput, String modelName) {
        TraceInfo traceInfo = TraceContext.get();
        String requestId = traceInfo != null && StrUtil.isNotBlank(traceInfo.requestId()) ? traceInfo.requestId() : UUID.randomUUID().toString();
        String traceId = traceInfo != null && StrUtil.isNotBlank(traceInfo.traceId()) ? traceInfo.traceId() : UUID.randomUUID().toString();
        String useSessionId = StrUtil.isNotBlank(sessionId)
                ? sessionId
                : traceInfo != null && StrUtil.isNotBlank(traceInfo.sessionId()) ? traceInfo.sessionId() : "default";
        requestLogRepository.insertStart(AgentRequestLogEntity.builder()
                .requestId(requestId)
                .traceId(traceId)
                .sessionId(useSessionId)
                .scene(scene)
                .userInput(truncate(userInput, 4000))
                .modelName(modelName)
                .status(ExecutionStatus.SUCCESS.name())
                .startedAt(LocalDateTime.now())
                .build());
        logStage(requestId, ExecutionStageType.INPUT, "user_input", null, truncate(userInput, 4000), null, 0L, true, null);
        return requestId;
    }

    @Override
    public void logStage(String requestId, ExecutionStageType stageType, String stageName, String toolName,
                         String inputPayload, String outputPayload, Long costMs, boolean success, String errorMessage) {
        TraceInfo traceInfo = TraceContext.get();
        stageLogRepository.insert(AgentStageLogEntity.builder()
                .requestId(requestId)
                .traceId(traceInfo == null ? null : traceInfo.traceId())
                .sessionId(traceInfo == null ? null : traceInfo.sessionId())
                .stageType(stageType.name())
                .stageName(stageName)
                .toolName(toolName)
                .inputPayload(truncate(inputPayload, 8000))
                .outputPayload(truncate(outputPayload, 8000))
                .costMs(costMs)
                .success(success)
                .errorMessage(truncate(errorMessage, 1000))
                .eventTime(LocalDateTime.now())
                .build());
    }

    @Override
    public void finishSuccess(String requestId, String finalOutput, long totalCostMs) {
        requestLogRepository.updateFinish(requestId, ExecutionStatus.SUCCESS, truncate(finalOutput, 12000), null, totalCostMs);
        logStage(requestId, ExecutionStageType.OUTPUT, "final_output", null, null, truncate(finalOutput, 8000), totalCostMs, true, null);
    }

    @Override
    public void finishFail(String requestId, String errorMessage, long totalCostMs) {
        requestLogRepository.updateFinish(requestId, ExecutionStatus.FAILED, null, truncate(errorMessage, 1000), totalCostMs);
        logStage(requestId, ExecutionStageType.ERROR, "request_error", null, null, null, totalCostMs, false, truncate(errorMessage, 1000));
    }

    @Override
    public RequestTraceVO queryTrace(String requestId) {
        AgentRequestLogEntity request = requestLogRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("requestId 不存在"));
        List<AgentStageLogEntity> stages = stageLogRepository.listByRequestId(requestId);
        return RequestTraceVO.builder().request(request).stages(stages).build();
    }

    @Override
    public List<AgentRequestLogEntity> queryBySession(String sessionId, int limit) {
        return requestLogRepository.listBySessionId(sessionId, Math.min(Math.max(limit, 1), 200));
    }

    @Override
    public List<AgentRequestLogEntity> queryFailures(LocalDateTime start, LocalDateTime end, int limit) {
        return requestLogRepository.listFailed(start, end, Math.min(Math.max(limit, 1), 500));
    }

    @Override
    public List<AgentStageLogEntity> queryStages(String requestId) {
        return stageLogRepository.listByRequestId(requestId);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
