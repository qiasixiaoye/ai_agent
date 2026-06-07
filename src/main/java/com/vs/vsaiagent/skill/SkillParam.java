package com.vs.vsaiagent.skill;

/**
 * Skill 的单个输入或输出参数 schema 描述。
 */
public record SkillParam(
        String name,
        String type,            // string / int / boolean / object / array
        String description,
        boolean required,
        Object defaultValue
) {
    public static SkillParam required(String name, String type, String description) {
        return new SkillParam(name, type, description, true, null);
    }

    public static SkillParam optional(String name, String type, String description, Object defaultValue) {
        return new SkillParam(name, type, description, false, defaultValue);
    }
}
