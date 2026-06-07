package com.vs.vsaiagent.skill;

import java.util.Collections;
import java.util.List;

/**
 * Skill 元数据。启动时由 SkillScanner 从 SKILL.md 解析得到，
 * 或由 Skill 实现类通过 defaultMetadata() 直接声明。
 *
 * 字段与 SKILL.md 的 YAML front-matter 一一对应，便于双向序列化。
 */
public record SkillMetadata(
        String name,
        String displayName,
        String description,
        String version,
        List<String> tags,
        List<SkillParam> inputs,
        List<SkillParam> outputs,
        List<String> examples,
        Long timeoutMs,
        SkillSourceType sourceType
) {

    /** 紧凑构造器：把可空集合归一化为空 List，避免下游写 null 检查 */
    public SkillMetadata {
        tags = tags == null ? Collections.emptyList() : List.copyOf(tags);
        inputs = inputs == null ? Collections.emptyList() : List.copyOf(inputs);
        outputs = outputs == null ? Collections.emptyList() : List.copyOf(outputs);
        examples = examples == null ? Collections.emptyList() : List.copyOf(examples);
        if (sourceType == null) {
            sourceType = SkillSourceType.LOCAL;
        }
        if (version == null || version.isBlank()) {
            version = "1.0.0";
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String displayName;
        private String description;
        private String version;
        private List<String> tags;
        private List<SkillParam> inputs;
        private List<SkillParam> outputs;
        private List<String> examples;
        private Long timeoutMs;
        private SkillSourceType sourceType;

        public Builder name(String v) { this.name = v; return this; }
        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder version(String v) { this.version = v; return this; }
        public Builder tags(List<String> v) { this.tags = v; return this; }
        public Builder inputs(List<SkillParam> v) { this.inputs = v; return this; }
        public Builder outputs(List<SkillParam> v) { this.outputs = v; return this; }
        public Builder examples(List<String> v) { this.examples = v; return this; }
        public Builder timeoutMs(Long v) { this.timeoutMs = v; return this; }
        public Builder sourceType(SkillSourceType v) { this.sourceType = v; return this; }

        public SkillMetadata build() {
            return new SkillMetadata(name, displayName, description, version,
                    tags, inputs, outputs, examples, timeoutMs, sourceType);
        }
    }
}
