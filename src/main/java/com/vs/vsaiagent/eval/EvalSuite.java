package com.vs.vsaiagent.eval;

import java.util.Collections;
import java.util.List;

/**
 * 一份评测数据集。从 classpath:eval/suites/*.yaml 加载。
 */
public record EvalSuite(
        String name,
        String description,
        String runner,        // 默认 runner 名（assistant_app / vs_manus 等）
        String judge,         // judge 名（keyword_contains / llm_as_judge）
        List<EvalCase> cases
) {
    public EvalSuite {
        cases = cases == null ? Collections.emptyList() : List.copyOf(cases);
        if (runner == null || runner.isBlank()) runner = "assistant_app";
        if (judge == null || judge.isBlank()) judge = "keyword_contains";
    }

    public int size() { return cases.size(); }
}
