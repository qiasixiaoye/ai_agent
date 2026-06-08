package com.vs.vsaiagent.dify.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Dify Workflow 执行的归一化结果。屏蔽掉 Dify 原始响应的繁琐字段。
 */
@Data
@Builder
public class DifyRunResult {
    private boolean success;
    private String workflowId;
    private String workflowRunId;
    private Object outputs;        // Dify workflow 的最终 outputs map
    private String status;         // succeeded / failed / running
    private String errorMessage;
    private Long elapsedMs;
    private String rawResponse;    // 调试用，前端可选不显示
}
