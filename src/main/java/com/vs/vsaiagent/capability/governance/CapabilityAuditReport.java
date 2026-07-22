package com.vs.vsaiagent.capability.governance;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record CapabilityAuditReport(
        int totalCapabilities,
        int toolCount,
        int skillCount,
        int issueCount,
        Map<String, Long> issuesBySeverity,
        List<CapabilityAuditIssue> issues,
        List<String> recommendations
) {
    public CapabilityAuditReport {
        issuesBySeverity = issuesBySeverity == null ? Collections.emptyMap() : Map.copyOf(issuesBySeverity);
        issues = issues == null ? Collections.emptyList() : List.copyOf(issues);
        recommendations = recommendations == null ? Collections.emptyList() : List.copyOf(recommendations);
    }
}

