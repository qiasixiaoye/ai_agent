package com.vs.vsaiagent.orchestration;

/** Evidence emitted to the client and trace for one enterprise field-research capability. */
public record FieldResearchEvidence(
        String capability,
        String status,
        String source,
        String observedAt,
        String summary,
        String fallbackReason) {
}
