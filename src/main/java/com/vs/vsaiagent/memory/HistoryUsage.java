package com.vs.vsaiagent.memory;

public record HistoryUsage(
        int storedMessages,
        int selectedMessages,
        int estimatedTokens,
        int tokenBudget,
        boolean truncated
) {
}
