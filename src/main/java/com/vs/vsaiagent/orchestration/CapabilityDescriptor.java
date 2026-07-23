package com.vs.vsaiagent.orchestration;

import java.util.List;

public record CapabilityDescriptor(
        String id,
        String type,
        String riskLevel,
        List<String> permissionScopes,
        boolean requiresConfirmation) {

    public CapabilityDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Capability id must not be blank");
        }
        type = type == null ? "tool" : type;
        riskLevel = riskLevel == null ? "LOW" : riskLevel.toUpperCase();
        permissionScopes = permissionScopes == null ? List.of() : List.copyOf(permissionScopes);
    }

    public boolean isHighRisk() {
        return "HIGH".equalsIgnoreCase(riskLevel)
                || permissionScopes.stream().anyMatch(scope -> List.of(
                        "FILE_WRITE", "EXTERNAL_WRITE", "SYSTEM_COMMAND", "PRIVATE_DATA", "PAYMENT", "DEPLOY"
                ).contains(scope.toUpperCase()));
    }
}
