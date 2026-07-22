package com.vs.vsaiagent.skill.routing;

import java.util.List;

/** Skill 路由结果：同时保留命中项和被拒绝的高分候选，便于审计与评测。 */
public record SkillRouteDecision(
        String query,
        double threshold,
        int requestedTopK,
        boolean matched,
        List<String> selectedSkillNames,
        List<SkillRouteCandidate> candidates
) {
    public SkillRouteDecision {
        selectedSkillNames = selectedSkillNames == null ? List.of() : List.copyOf(selectedSkillNames);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
