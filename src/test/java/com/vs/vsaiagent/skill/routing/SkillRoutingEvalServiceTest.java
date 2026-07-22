package com.vs.vsaiagent.skill.routing;

import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillResult;
import com.vs.vsaiagent.skill.loader.SkillMdParser;
import com.vs.vsaiagent.skill.registry.InMemorySkillRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillRoutingEvalServiceTest {

    @Test
    void evaluatesRealSkillMetadataAgainstVersionedSuite() throws Exception {
        InMemorySkillRegistry registry = new InMemorySkillRegistry();
        for (String name : List.of("astro-shoot-plan", "coffee-tasting-notes",
                "moto-gear-checklist", "pdf-generation")) {
            SkillMetadata metadata;
            try (var input = new ClassPathResource("skills/" + name + "/SKILL.md").getInputStream()) {
                metadata = SkillMdParser.parse(input);
            }
            registry.register(metadataOnlySkill(metadata));
        }

        SkillRoutingEvalReport report = new SkillRoutingEvalService(
                new WeightedSkillRouter(registry, 3, 0.24)).evaluate();

        assertEquals(10, report.total());
        assertEquals(report.total(), report.correct(), report.toString());
        assertEquals(1.0, report.accuracy());
        assertEquals(0, report.falsePositives());
        assertEquals(0, report.falseNegatives());
    }

    private Skill metadataOnlySkill(SkillMetadata metadata) {
        return new Skill() {
            @Override
            public SkillMetadata metadata() {
                return metadata;
            }

            @Override
            public SkillResult execute(Map<String, Object> arguments, SkillContext context) {
                return SkillResult.ok(Map.of(), 0);
            }
        };
    }
}
