package com.vs.vsaiagent.skill.controller;

import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.SkillSourceType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Skill 详情 VO（带完整 inputs/outputs/examples）。
 */
@Data
@Builder
public class SkillDetailVO {
    private String name;
    private String displayName;
    private String description;
    private String version;
    private List<String> tags;
    private SkillSourceType sourceType;
    private List<SkillParam> inputs;
    private List<SkillParam> outputs;
    private List<String> examples;
    private Long timeoutMs;

    public static SkillDetailVO from(SkillMetadata md) {
        return SkillDetailVO.builder()
                .name(md.name())
                .displayName(md.displayName())
                .description(md.description())
                .version(md.version())
                .tags(md.tags())
                .sourceType(md.sourceType())
                .inputs(md.inputs())
                .outputs(md.outputs())
                .examples(md.examples())
                .timeoutMs(md.timeoutMs())
                .build();
    }
}
