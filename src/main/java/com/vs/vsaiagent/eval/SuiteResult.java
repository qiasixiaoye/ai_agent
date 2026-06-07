package com.vs.vsaiagent.eval;

import java.util.Collections;
import java.util.List;

/**
 * 整份 suite 的聚合执行结果。
 */
public record SuiteResult(
        String suiteName,
        String runner,
        String judge,
        int total,
        int passed,
        int failed,
        long totalElapsedMs,
        List<CaseResult> cases
) {
    public SuiteResult {
        cases = cases == null ? Collections.emptyList() : List.copyOf(cases);
    }

    public double passRate() {
        if (total == 0) return 0.0;
        return ((double) passed) / total;
    }
}
