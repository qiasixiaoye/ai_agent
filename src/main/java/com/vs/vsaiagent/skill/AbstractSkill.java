package com.vs.vsaiagent.skill;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Skill 默认实现基类。
 *
 * 子类只需要：
 *  1. 重写 defaultMetadata() 返回元数据（也可以放进 SKILL.md 由 Scanner 自动注入）
 *  2. 实现 doExecute(args, ctx)
 *
 * 基类负责：
 *  - 必填参数校验
 *  - 计时
 *  - 异常包装为 SkillResult.fail
 */
@Slf4j
public abstract class AbstractSkill implements Skill {

    private volatile SkillMetadata metadata;

    @Override
    public final SkillMetadata metadata() {
        if (metadata == null) {
            synchronized (this) {
                if (metadata == null) {
                    metadata = defaultMetadata();
                }
            }
        }
        return metadata;
    }

    /** 由 SkillScanner 在解析到对应 SKILL.md 后注入，覆盖代码默认值。 */
    public final void overrideMetadata(SkillMetadata md) {
        this.metadata = md;
    }

    protected abstract SkillMetadata defaultMetadata();

    protected abstract Object doExecute(Map<String, Object> arguments, SkillContext context) throws Exception;

    @Override
    public final SkillResult execute(Map<String, Object> arguments, SkillContext context) {
        long start = System.currentTimeMillis();
        try {
            validate(arguments);
            Object data = doExecute(arguments == null ? Map.of() : arguments,
                    context == null ? SkillContext.empty() : context);
            return SkillResult.ok(data, System.currentTimeMillis() - start);
        } catch (IllegalArgumentException e) {
            log.warn("[skill] {} arguments invalid: {}", name(), e.getMessage());
            return SkillResult.fail(e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[skill] {} execute failed", name(), e);
            return SkillResult.fail(e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void validate(Map<String, Object> arguments) {
        SkillMetadata md = metadata();
        if (md.inputs() == null || md.inputs().isEmpty()) {
            return;
        }
        for (SkillParam p : md.inputs()) {
            if (!p.required()) continue;
            Object v = arguments == null ? null : arguments.get(p.name());
            if (v == null || (v instanceof String s && s.isBlank())) {
                throw new IllegalArgumentException("skill[" + name() + "] missing required param: " + p.name());
            }
        }
    }
}
