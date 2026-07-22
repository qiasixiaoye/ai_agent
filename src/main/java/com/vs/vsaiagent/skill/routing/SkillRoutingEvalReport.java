package com.vs.vsaiagent.skill.routing;

import java.util.List;

/** 离线 Skill 路由评测结果，可直接作为调参和简历指标的可复现实验依据。 */
public record SkillRoutingEvalReport(
        String suiteName,
        int total,
        int correct,
        double accuracy,
        int falsePositives,
        int falseNegatives,
        List<CaseResult> cases
) {
    public record CaseResult(
            String query,
            String expectedSkill,
            String actualSkill,
            boolean correct,
            double topScore
    ) {
    }
}
