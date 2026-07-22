package com.vs.vsaiagent.skill.routing;

import java.util.List;

/** 一个 Skill 的可解释路由评分。 */
public record SkillRouteCandidate(
        String skillName,
        String displayName,
        double score,
        boolean selected,
        List<String> reasons
) {
    public SkillRouteCandidate {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
