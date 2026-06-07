package com.vs.vsaiagent.eval.judge;

import com.vs.vsaiagent.eval.CaseResult;
import com.vs.vsaiagent.eval.EvalCase;
import com.vs.vsaiagent.eval.EvalJudge;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 期望关键词全部命中即通过。命中匹配不区分大小写。
 */
@Component
public class KeywordContainsJudge implements EvalJudge {

    public static final String NAME = "keyword_contains";

    @Override
    public String name() { return NAME; }

    @Override
    public CaseResult judge(EvalCase evalCase, String actualOutput) {
        String body = actualOutput == null ? "" : actualOutput.toLowerCase();
        List<String> missed = new ArrayList<>();
        for (String kw : evalCase.expectedContains()) {
            if (kw == null || kw.isBlank()) continue;
            if (!body.contains(kw.toLowerCase())) {
                missed.add(kw);
            }
        }
        boolean pass = missed.isEmpty();
        String reason = pass
                ? "all keywords matched"
                : "missing keywords: " + String.join(", ", missed);
        return new CaseResult(
                evalCase.id(),
                evalCase.input(),
                actualOutput,
                pass,
                reason,
                missed,
                0L,
                0L
        );
    }
}
