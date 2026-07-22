package com.vs.vsaiagent.capability.governance;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public record CapabilitySecurityContract(
        String riskLevel,
        List<String> permissionScopes,
        String sideEffects,
        String dataSensitivity,
        Boolean requiresConfirmation,
        String reviewStatus,
        String lifecycleStatus,
        List<String> allowedDomains,
        List<String> allowedPaths
) {
    public CapabilitySecurityContract {
        riskLevel = upperOrNull(riskLevel);
        permissionScopes = permissionScopes == null
                ? Collections.emptyList()
                : permissionScopes.stream().filter(s -> s != null && !s.isBlank()).map(CapabilitySecurityContract::upper).toList();
        sideEffects = upperOrNull(sideEffects);
        dataSensitivity = upperOrNull(dataSensitivity);
        reviewStatus = upperOrNull(reviewStatus);
        lifecycleStatus = upperOrNull(lifecycleStatus);
        allowedDomains = allowedDomains == null ? Collections.emptyList() : List.copyOf(allowedDomains);
        allowedPaths = allowedPaths == null ? Collections.emptyList() : List.copyOf(allowedPaths);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String upper(String value) {
        return value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static String upperOrNull(String value) {
        return value == null || value.isBlank() ? null : upper(value);
    }

    public static final class Builder {
        private String riskLevel;
        private List<String> permissionScopes;
        private String sideEffects;
        private String dataSensitivity;
        private Boolean requiresConfirmation;
        private String reviewStatus;
        private String lifecycleStatus;
        private List<String> allowedDomains;
        private List<String> allowedPaths;

        public Builder riskLevel(String v) { this.riskLevel = v; return this; }
        public Builder permissionScopes(List<String> v) { this.permissionScopes = v; return this; }
        public Builder sideEffects(String v) { this.sideEffects = v; return this; }
        public Builder dataSensitivity(String v) { this.dataSensitivity = v; return this; }
        public Builder requiresConfirmation(Boolean v) { this.requiresConfirmation = v; return this; }
        public Builder reviewStatus(String v) { this.reviewStatus = v; return this; }
        public Builder lifecycleStatus(String v) { this.lifecycleStatus = v; return this; }
        public Builder allowedDomains(List<String> v) { this.allowedDomains = v; return this; }
        public Builder allowedPaths(List<String> v) { this.allowedPaths = v; return this; }

        public CapabilitySecurityContract build() {
            return new CapabilitySecurityContract(riskLevel, permissionScopes, sideEffects, dataSensitivity,
                    requiresConfirmation, reviewStatus, lifecycleStatus, allowedDomains, allowedPaths);
        }
    }
}
