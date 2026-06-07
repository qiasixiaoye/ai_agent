package com.vs.vsaiagent.skill.loader;

import com.vs.vsaiagent.skill.AbstractSkill;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时扫描 classpath:skills/{name}/SKILL.md，将解析出的元数据
 * 注入对应的 AbstractSkill Bean，并把所有 Skill 注册到 SkillRegistry。
 */
@Slf4j
@Component
public class SkillScanner {

    private static final String SKILLS_LOCATION = "classpath:skills/**/SKILL.md";

    private final SkillRegistry skillRegistry;
    private final List<Skill> skills;
    private final ResourcePatternResolver resolver;

    @Autowired
    public SkillScanner(SkillRegistry skillRegistry, List<Skill> skills) {
        this.skillRegistry = skillRegistry;
        this.skills = skills;
        this.resolver = new PathMatchingResourcePatternResolver();
    }

    @PostConstruct
    public void scanAndRegister() {
        Map<String, SkillMetadata> mdByName = scanMarkdown();
        for (Skill skill : skills) {
            // 默认拿代码里 defaultMetadata，再尝试用 SKILL.md 覆盖
            String name = skill.metadata().name();
            SkillMetadata fromMd = mdByName.get(name);
            if (fromMd != null && skill instanceof AbstractSkill abstractSkill) {
                abstractSkill.overrideMetadata(fromMd);
                log.info("[skill-scanner] metadata of '{}' overridden by SKILL.md", name);
            }
            skillRegistry.register(skill);
        }
        log.info("[skill-scanner] registered {} skills, {} SKILL.md parsed",
                skills.size(), mdByName.size());
    }

    private Map<String, SkillMetadata> scanMarkdown() {
        Map<String, SkillMetadata> result = new HashMap<>();
        try {
            Resource[] resources = resolver.getResources(SKILLS_LOCATION);
            for (Resource r : resources) {
                try {
                    SkillMetadata md = SkillMdParser.parse(r.getInputStream());
                    if (md != null && md.name() != null) {
                        result.put(md.name(), md);
                    }
                } catch (IOException ioe) {
                    log.warn("[skill-scanner] read {} failed", r.getFilename(), ioe);
                }
            }
        } catch (IOException e) {
            log.warn("[skill-scanner] resolve {} failed: {}", SKILLS_LOCATION, e.getMessage());
        }
        return result;
    }
}
