package com.vs.vsaiagent.capability.governance;

public record CapabilityAuditIssue(
        String capabilityType,
        String capabilityName,
        String severity,
        String category,
        String message,
        String recommendation
) {
}

