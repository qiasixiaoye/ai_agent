package com.vs.vsaiagent.skill;

import java.util.Map;

/**
 * Skill 的标准返回结构。data 字段对应业务实际产出，
 * metrics 留给将来塞 token 数、外部 API 耗时等可观测指标。
 */
public record SkillResult(
        boolean success,
        Object data,
        String errorMessage,
        long elapsedMs,
        Map<String, Object> metrics
) {

    public static SkillResult ok(Object data, long elapsedMs) {
        return new SkillResult(true, data, null, elapsedMs, Map.of());
    }

    public static SkillResult ok(Object data, long elapsedMs, Map<String, Object> metrics) {
        return new SkillResult(true, data, null, elapsedMs, metrics == null ? Map.of() : metrics);
    }

    public static SkillResult fail(String errorMessage, long elapsedMs) {
        return new SkillResult(false, null, errorMessage, elapsedMs, Map.of());
    }
}
