package com.vs.vsaiagent.orchestration;

public record OrchestrationRequest(
        String conversationId,
        String message,
        String confirmationToken,
        String requestId,
        String traceId) {
}
