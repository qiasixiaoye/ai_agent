package com.vs.vsaiagent.eval;

import java.util.Collections;
import java.util.List;

/**
 * 单个评测样例。
 *
 * @param id                业务唯一 id
 * @param input             喂给被测对象的输入
 * @param expectedContains  期望命中的关键词列表（KeywordContainsJudge 用）
 * @param rubric            自由文本评分准则（保留给未来 LlmAsJudge）
 * @param tags              便于分组筛选
 */
public record EvalCase(
        String id,
        String input,
        List<String> expectedContains,
        String rubric,
        List<String> tags
) {
    public EvalCase {
        expectedContains = expectedContains == null ? Collections.emptyList() : List.copyOf(expectedContains);
        tags = tags == null ? Collections.emptyList() : List.copyOf(tags);
    }
}
