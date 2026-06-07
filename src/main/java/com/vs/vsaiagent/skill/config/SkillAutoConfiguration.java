package com.vs.vsaiagent.skill.config;

import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.adapter.SkillCallbackAdapter;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.List;

/**
 * Skill 模块的装配入口。提供一个 Skill 适配版的 ToolCallback[] Bean，
 * 用 @Qualifier("skillTools") 引用，避免覆盖现有 allTools。
 *
 * 后续步骤里，ToolRegistration 会被改造为合并 allTools + skillTools，
 * 最终把老 tools/*Tool.java 替换为 Skill 形态。
 */
@Configuration
public class SkillAutoConfiguration {

    @Bean(name = "skillTools")
    @DependsOn("skillScanner")
    public ToolCallback[] skillTools(SkillRegistry skillRegistry) {
        List<Skill> all = skillRegistry.listAll();
        ToolCallback[] callbacks = new ToolCallback[all.size()];
        for (int i = 0; i < all.size(); i++) {
            callbacks[i] = new SkillCallbackAdapter(all.get(i));
        }
        return callbacks;
    }
}
