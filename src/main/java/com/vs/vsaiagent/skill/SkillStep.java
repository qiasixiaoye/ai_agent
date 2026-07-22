package com.vs.vsaiagent.skill;

/**
 * Skill 的一个内部步骤。这是 Skill 区别于「单个工具/接口」的核心：
 * 一个 Skill 是一段<b>有序的内部过程</b>，可由若干步骤组成，步骤可以引用(调用)已注册的工具/技能。
 *
 * 来源有二：
 *  - SKILL.md front-matter 的 {@code steps:} 列表（声明式，给人看 + 给规划/编排参考）；
 *  - 结构化 Skill 实现里 {@code doExecute} 实际按这些步骤编排工具（执行式）。
 *
 * @param name        步骤名（如 "查询银河升起时间"）
 * @param uses        该步调用的能力引用，形如 {@code tool:milkyway_rise} / {@code skill:xxx}；
 *                    纯计算/纯 LLM 步骤可为 null
 * @param description 该步做什么（一句话）
 */
public record SkillStep(
        String name,
        String uses,
        String description
) {
    public static SkillStep of(String name, String uses, String description) {
        return new SkillStep(name, uses, description);
    }
}
