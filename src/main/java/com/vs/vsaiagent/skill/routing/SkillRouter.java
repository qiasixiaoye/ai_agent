package com.vs.vsaiagent.skill.routing;

public interface SkillRouter {

    SkillRouteDecision route(String query);

    SkillRouteDecision route(String query, int topK, double threshold);
}
