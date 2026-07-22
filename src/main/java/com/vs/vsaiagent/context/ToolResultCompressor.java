package com.vs.vsaiagent.context;

import org.springframework.stereotype.Component;

/** Prevents one large tool response from monopolizing the current tool loop. */
@Component
public class ToolResultCompressor {

    private final TokenEstimator estimator;
    private final ContextBudgetManager budgetManager;

    public ToolResultCompressor(TokenEstimator estimator, ContextBudgetManager budgetManager) {
        this.estimator = estimator;
        this.budgetManager = budgetManager;
    }

    public CompressionResult compress(String output) {
        return compress(output, budgetManager.cap(ContextBudgetManager.TOOL_RESULT));
    }

    public CompressionResult compress(String output, int maxTokens) {
        String value = output == null ? "" : output;
        int original = estimator.estimate(value);
        if (original <= maxTokens) {
            return new CompressionResult(value, original, original, false, "none");
        }
        String body = estimator.fitHeadTail(value, Math.max(64, maxTokens - 48));
        String header = "[tool result compressed: originalTokens=" + original
                + ", strategy=head-tail, full result kept only in audit/storage when available]\n";
        String compressed = estimator.fit(header + body, maxTokens);
        return new CompressionResult(compressed, original, estimator.estimate(compressed), true, "head-tail");
    }
}
