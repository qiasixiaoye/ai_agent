package com.vs.vsaiagent.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultCompressorTest {

    @Test
    void keepsSmallResultsAndCompressesLargeResultsWithinTokenLimit() {
        TokenEstimator estimator = new TokenEstimator();
        ToolResultCompressor compressor = new ToolResultCompressor(
                estimator, ContextBudgetManagerTest.manager());

        assertFalse(compressor.compress("small result", 100).compressed());

        String large = "BEGIN-" + "data-line\n".repeat(8_000) + "-END";
        CompressionResult result = compressor.compress(large, 500);
        assertTrue(result.compressed());
        assertTrue(result.content().contains("BEGIN"));
        assertTrue(result.content().contains("END"));
        assertTrue(result.retainedTokens() <= 500);
        assertTrue(result.originalTokens() > result.retainedTokens());
    }
}
