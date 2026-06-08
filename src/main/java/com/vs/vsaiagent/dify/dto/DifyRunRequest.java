package com.vs.vsaiagent.dify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 触发 Dify Workflow 时的请求体。前端 / Java 侧用。
 *
 * @param workflowId  目标 workflow（覆盖 default）。如果为空则用 DifyProperties.defaultWorkflowId
 * @param inputs      Dify 工作流入参（按你 workflow 自己定义的变量）
 * @param user        Dify 要求的 end-user 标识，可用 traceId / chatId
 * @param responseMode 'blocking' 直接拿结果；'streaming' 当前未启用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DifyRunRequest {
    private String workflowId;
    private Map<String, Object> inputs;
    private String user;
    private String responseMode;
}
