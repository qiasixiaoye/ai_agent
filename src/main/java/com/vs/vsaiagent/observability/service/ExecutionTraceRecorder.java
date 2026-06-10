package com.vs.vsaiagent.observability.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Controller 层使用的轻量观测门面。
 * 业务接口不应因为观测写入失败而中断，因此这里统一吞掉记录异常并写 warn 日志。
 */
@Slf4j
@Component
public class ExecutionTraceRecorder {

    private final ExecutionLogService executionLogService;
    private final ObjectMapper objectMapper;

    public ExecutionTraceRecorder(ExecutionLogService executionLogService, ObjectMapper objectMapper) {
        this.executionLogService = executionLogService;
        this.objectMapper = objectMapper;
    }

    public String start(String scene, Object inputPayload, String modelName) {
        try {
            return executionLogService.startRequest(scene, null, toPayload(inputPayload), modelName);
        } catch (Exception e) {
            log.warn("[observability] start request log failed scene={}", scene, e);
            return null;
        }
    }

    public void stage(String requestId,
                      ExecutionStageType stageType,
                      String stageName,
                      String toolName,
                      Object inputPayload,
                      Object outputPayload,
                      Long costMs,
                      boolean success,
                      String errorMessage) {
        if (StrUtil.isBlank(requestId)) {
            return;
        }
        try {
            executionLogService.logStage(requestId, stageType, stageName, toolName,
                    toPayload(inputPayload), toPayload(outputPayload), costMs, success, errorMessage);
        } catch (Exception e) {
            log.warn("[observability] stage log failed requestId={} stage={}", requestId, stageName, e);
        }
    }

    public void success(String requestId, Object finalOutput, long startedAtMs) {
        if (StrUtil.isBlank(requestId)) {
            return;
        }
        try {
            executionLogService.finishSuccess(requestId, toPayload(finalOutput), elapsedMs(startedAtMs));
        } catch (Exception e) {
            log.warn("[observability] finish success log failed requestId={}", requestId, e);
        }
    }

    public void fail(String requestId, Throwable error, long startedAtMs) {
        fail(requestId, error == null ? null : error.getMessage(), startedAtMs);
    }

    public void fail(String requestId, String errorMessage, long startedAtMs) {
        if (StrUtil.isBlank(requestId)) {
            return;
        }
        try {
            executionLogService.finishFail(requestId, errorMessage, elapsedMs(startedAtMs));
        } catch (Exception e) {
            log.warn("[observability] finish fail log failed requestId={}", requestId, e);
        }
    }

    private long elapsedMs(long startedAtMs) {
        return Math.max(0, System.currentTimeMillis() - startedAtMs);
    }

    private String toPayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return String.valueOf(payload);
        }
    }
}
