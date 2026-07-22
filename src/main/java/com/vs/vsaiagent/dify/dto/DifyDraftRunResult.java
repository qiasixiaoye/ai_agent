package com.vs.vsaiagent.dify.dto;

import java.util.List;

/**
 * Dify「草稿运行」（draft run）的观测结果：把 Dify 返回的 SSE 事件流解析成结构化运行轨迹，
 * 让 Agent / 工作流的**运行时状态与错误**对后端可见（对接错误分析审计）。
 *
 * @param success     整体是否成功（workflow_finished.status == succeeded）
 * @param status      整体状态（succeeded / failed / stopped）
 * @param error       整体错误（失败时）
 * @param elapsedTime 总耗时（秒）
 * @param totalTokens 总 token
 * @param answer      最终回复文本（chat 流的 message 拼接）
 * @param nodes       各节点执行结果（含失败节点及其 error）
 * @param agentRounds Agent 节点内部的自主调度轮次（ROUND N，含每轮 error/status）
 * @param requestId   后端 observability 的请求 id，可据此在审计模块回查
 * @param correlatedToolCalls 启发式关联到本次运行（时间窗 + 工具名）的后端 MCP 工具调用，可据各自 requestId 深查入参/报错
 */
public record DifyDraftRunResult(
        boolean success,
        String status,
        String error,
        Double elapsedTime,
        Integer totalTokens,
        String answer,
        List<NodeStep> nodes,
        List<AgentRound> agentRounds,
        String requestId,
        List<CorrelatedToolCall> correlatedToolCalls
) {
    /**
     * 一次被关联到的后端 MCP 工具调用（来自 scene=mcp-tool-call 的 observability 记录）。
     * 这是<b>启发式关联</b>（时间窗 + 工具名），并非精确因果对账。
     */
    public record CorrelatedToolCall(String requestId, String toolName, boolean success, Long costMs) {
    }

    /** 单个节点的执行结果。 */
    public record NodeStep(String nodeType, String title, String status, String error, Double elapsedTime) {
        public boolean ok() {
            return "succeeded".equalsIgnoreCase(status);
        }
    }

    /** Agent 节点内部一轮自主调度（function_calling / ReAct 的 ROUND）。 */
    public record AgentRound(String label, String nodeId, String status, String error) {
        public boolean failed() {
            return (error != null && !error.isBlank())
                    || "error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status);
        }
    }
}
