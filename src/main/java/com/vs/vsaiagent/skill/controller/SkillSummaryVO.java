package com.vs.vsaiagent.skill.controller;

import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillSourceType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Skill 列表项 VO（轻量级，不带 inputs/outputs/examples）。
 */
@Data
@Builder
public class SkillSummaryVO {
    private String name;
    private String displayName;
    private String description;
    private String version;
    private List<String> tags;
    private SkillSourceType sourceType;

    public static SkillSummaryVO from(SkillMetadata md) {
        return SkillSummaryVO.builder()
                .name(md.name())
                .displayName(md.displayName())
                .description(md.description())
                .version(md.version())
                .tags(md.tags())
                .sourceType(md.sourceType())
                .build();
    }
}
