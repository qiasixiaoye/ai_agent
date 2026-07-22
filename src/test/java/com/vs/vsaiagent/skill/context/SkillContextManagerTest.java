package com.vs.vsaiagent.skill.context;

import com.vs.vsaiagent.context.ContextBudgetManager;
import com.vs.vsaiagent.context.ContextBudgetManagerTest;
import com.vs.vsaiagent.context.TokenEstimator;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillResult;
import com.vs.vsaiagent.skill.registry.InMemorySkillRegistry;
import com.vs.vsaiagent.skill.routing.SkillRouteDecision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillContextManagerTest {

    @Test
    void loadsOnlySelectedSkillManualAndAppliesPerSkillBudget() {
        InMemorySkillRegistry registry = new InMemorySkillRegistry();
        registry.register(skill("selected", "important-start\n" + "detail ".repeat(3_000)));
        registry.register(skill("not-selected", "must-not-load"));
        ContextBudgetManager budget = ContextBudgetManagerTest.manager();
        SkillContextManager manager = new SkillContextManager(
                registry, new TokenEstimator(), budget, 300);

        SkillContextAssembly result = manager.assemble(new SkillRouteDecision(
                "query", 0.2, 3, true, List.of("selected"), List.of()));

        assertEquals(List.of("selected"), result.loadedSkills());
        assertTrue(result.contextText().contains("important-start"));
        assertTrue(!result.contextText().contains("must-not-load"));
        assertTrue(result.tokensBySkill().get("selected") <= 300);
    }

    private Skill skill(String name, String instructions) {
        return new Skill() {
            @Override public SkillMetadata metadata() {
                return SkillMetadata.builder().name(name).description(name)
                        .instructions(instructions).build();
            }
            @Override public SkillResult execute(Map<String, Object> arguments, SkillContext context) {
                return SkillResult.ok("ok", 0);
            }
        };
    }
}
