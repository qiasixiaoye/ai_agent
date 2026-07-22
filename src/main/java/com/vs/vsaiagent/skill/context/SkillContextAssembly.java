package com.vs.vsaiagent.skill.context;

import java.util.List;
import java.util.Map;

public record SkillContextAssembly(
        String contextText,
        int estimatedTokens,
        int tokenBudget,
        List<String> loadedSkills,
        List<String> droppedSkills,
        Map<String, Integer> tokensBySkill
) {
    public SkillContextAssembly {
        loadedSkills = loadedSkills == null ? List.of() : List.copyOf(loadedSkills);
        droppedSkills = droppedSkills == null ? List.of() : List.copyOf(droppedSkills);
        tokensBySkill = tokensBySkill == null ? Map.of() : Map.copyOf(tokensBySkill);
    }

    public static SkillContextAssembly empty(int budget) {
        return new SkillContextAssembly("", 0, budget, List.of(), List.of(), Map.of());
    }
}
