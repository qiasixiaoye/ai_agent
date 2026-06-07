package com.vs.vsaiagent.skill;

import java.util.Map;

/**
 * Skill 顶层抽象。
 *
 * 所有可被 ChatClient / agentplatform / MCP Server 调用的「能力单元」均实现此接口。
 * 一个 Skill = 一份 SKILL.md 元数据 + 一个执行方法。
 */
public interface Skill {

    /** 元数据。可由 SKILL.md 解析得到，也可由实现类直接声明。 */
    SkillMetadata metadata();

    /**
     * 执行 Skill。
     *
     * @param arguments 入参，名字与 metadata().inputs() 对应
     * @param context   执行上下文（trace/request/session/业务透传）
     * @return 标准 SkillResult
     */
    SkillResult execute(Map<String, Object> arguments, SkillContext context);

    /** 便捷方法：默认拿元数据里的 name */
    default String name() {
        return metadata().name();
    }
}
