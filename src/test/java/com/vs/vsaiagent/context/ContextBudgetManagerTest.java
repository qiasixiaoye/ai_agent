package com.vs.vsaiagent.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContextBudgetManagerTest {

    @Test
    void reservesOutputAndSafetyAndShrinksDynamicCategoriesUnderPressure() {
        ContextBudgetManager manager = manager();
        String largeSystem = "中".repeat(24_000);

        ContextBudgetPlan plan = manager.plan(largeSystem, "question", "", null);

        assertEquals(26_624, plan.maxInputTokens());
        assertTrue(plan.estimatedTotalInputTokens() <= plan.maxInputTokens());
        assertEquals("CRITICAL", plan.pressureLevel());
        assertTrue(plan.budget(ContextBudgetManager.HISTORY) < 5_000);
    }

    public static ContextBudgetManager manager() {
        return new ContextBudgetManager(new TokenEstimator(),
                32_768, 4_096, 2_048,
                5_000, 1_500, 1_000, 1_000, 5_000,
                3_500, 2_000, 3_500);
    }
}
