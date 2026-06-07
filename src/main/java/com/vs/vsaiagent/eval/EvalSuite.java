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
        List<EvalCase> cases
) {
    public EvalSuite {
        cases = cases == null ? Collections.emptyList() : List.copyOf(cases);
        if (runner == null || runner.isBlank()) runner = "assistant_app";
    }

    public int size() { return cases.size(); }
}
