package com.vs.vsaiagent.orchestration;

import java.util.Map;
import java.util.Objects;

/** Normalized output from a knowledge retrieval, Tool, or Skill invocation. */
public record Evidence(
        String sourceType,
        String sourceId,
        String status,
        String summary,
        Map<String, Object> provenance,
        Object payload) {

    public Evidence {
        requireText(sourceType, "sourceType");
        requireText(sourceId, "sourceId");
        requireText(status, "status");
        summary = summary == null ? "" : summary;
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    }

    public static Evidence success(String sourceType, String sourceId, String summary,
                                   Map<String, Object> provenance, Object payload) {
        return new Evidence(sourceType, sourceId, "success", summary, provenance, payload);
    }

    public static Evidence empty(String sourceType, String sourceId, String summary,
                                 Map<String, Object> provenance) {
        return new Evidence(sourceType, sourceId, "empty", summary, provenance, ListPayload.empty());
    }

    public static Evidence failed(String sourceType, String sourceId, String summary,
                                  Map<String, Object> provenance) {
        return new Evidence(sourceType, sourceId, "failed", summary, provenance, ListPayload.empty());
    }

    private static void requireText(String value, String name) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static final class ListPayload {
        private ListPayload() {
        }

        private static Object empty() {
            return java.util.List.of();
        }
    }
}
