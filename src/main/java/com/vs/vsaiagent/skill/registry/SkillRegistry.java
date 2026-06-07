package com.vs.vsaiagent.skill.registry;

import com.vs.vsaiagent.skill.Skill;

import java.util.List;
import java.util.Optional;

/**
 * Skill 注册中心。负责统一管理所有可被调用的 Skill。
 */
public interface SkillRegistry {

    void register(Skill skill);

    Optional<Skill> find(String name);

    List<Skill> listAll();

    List<Skill> listByTag(String tag);

    boolean unregister(String name);
}
