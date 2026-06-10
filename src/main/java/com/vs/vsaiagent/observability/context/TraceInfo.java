package com.vs.vsaiagent.observability.context;

public record TraceInfo(String traceId, String requestId, String sessionId) {
}
