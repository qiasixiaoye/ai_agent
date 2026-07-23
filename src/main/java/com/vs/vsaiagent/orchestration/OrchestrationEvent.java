package com.vs.vsaiagent.orchestration;

import java.util.Map;
import java.util.Objects;

/** A traceable event emitted by the unified request orchestration pipeline. */
public record OrchestrationEvent(
        String type,
        String conversationId,
        String requestId,
        String traceId,
        Map<String, Object> payload) {

    public OrchestrationEvent {
        requireText(type, "type");
        requireText(conversationId, "conversationId");
        requireText(requestId, "requestId");
        requireText(traceId, "traceId");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static OrchestrationEvent of(String type, String conversationId, String requestId,
                                        String traceId, Map<String, Object> payload) {
        return new OrchestrationEvent(type, conversationId, requestId, traceId, payload);
    }

    private static void requireText(String value, String name) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
