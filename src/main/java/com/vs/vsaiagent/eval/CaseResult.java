package com.vs.vsaiagent.eval;

import java.util.Collections;
import java.util.List;

/**
 * 单个 case 的执行 + 判分结果。
 */
public record CaseResult(
        String caseId,
        String input,
        String actualOutput,
        boolean pass,
        String reason,                // 失败原因 / 评语
        List<String> missedKeywords,  // KeywordContainsJudge 命中失败的关键词
        long runnerElapsedMs,
        long judgeElapsedMs
) {
    public CaseResult {
        missedKeywords = missedKeywords == null ? Collections.emptyList() : List.copyOf(missedKeywords);
    }
}
