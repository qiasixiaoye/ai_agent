package com.vs.vsaiagent.context;

import java.util.Map;

public record ContextBudgetPlan(
        int contextWindowTokens,
        int outputReserveTokens,
        int safetyMarginTokens,
        int maxInputTokens,
        int fixedInputTokens,
        int allocatableTokens,
        int estimatedTotalInputTokens,
        double utilization,
        String pressureLevel,
        Map<String, Integer> fixedTokens,
        Map<String, Integer> categoryBudgets
) {
    public ContextBudgetPlan {
        fixedTokens = fixedTokens == null ? Map.of() : Map.copyOf(fixedTokens);
        categoryBudgets = categoryBudgets == null ? Map.of() : Map.copyOf(categoryBudgets);
    }

    public int budget(String category) {
        return categoryBudgets.getOrDefault(category, 0);
    }
}
