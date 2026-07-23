package com.vs.vsaiagent.orchestration;

public record PolicyDecision(boolean allowed, boolean requiresConfirmation, String reason) {

    public static PolicyDecision allowed(boolean requiresConfirmation, String reason) {
        return new PolicyDecision(true, requiresConfirmation, reason);
    }

    public static PolicyDecision blocked(String reason) {
        return new PolicyDecision(false, false, reason);
    }
}
