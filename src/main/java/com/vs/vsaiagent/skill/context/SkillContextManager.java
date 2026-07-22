package com.vs.vsaiagent.skill.context;

import com.vs.vsaiagent.context.ContextBudgetManager;
import com.vs.vsaiagent.context.TokenEstimator;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillStep;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import com.vs.vsaiagent.skill.routing.SkillRouteDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Progressive disclosure: route on metadata, load full manuals only for selected skills. */
@Component
public class SkillContextManager {

    private final SkillRegistry registry;
    private final TokenEstimator estimator;
    private final ContextBudgetManager budgetManager;
    private final int perSkillBudget;

    public SkillContextManager(SkillRegistry registry, TokenEstimator estimator,
                               ContextBudgetManager budgetManager,
                               @Value("${app.context.skill-per-item-token-budget:2000}") int perSkillBudget) {
        this.registry = registry;
        this.estimator = estimator;
        this.budgetManager = budgetManager;
        this.perSkillBudget = Math.max(128, perSkillBudget);
    }

    public SkillContextAssembly assemble(SkillRouteDecision decision) {
        int totalBudget = budgetManager.cap(ContextBudgetManager.SKILLS);
        if (decision == null || decision.selectedSkillNames().isEmpty()) {
            return SkillContextAssembly.empty(totalBudget);
        }
        StringBuilder out = new StringBuilder("\n\n[loaded skill manuals]\n");
        List<String> loaded = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        LinkedHashMap<String, Integer> tokensBySkill = new LinkedHashMap<>();

        for (String name : decision.selectedSkillNames()) {
            Skill skill = registry.find(name).orElse(null);
            if (skill == null) {
                dropped.add(name + ":not-registered");
                continue;
            }
            String manual = renderManual(skill.metadata());
            manual = estimator.fit(manual, perSkillBudget);
            int tokens = estimator.estimate(manual);
            if (estimator.estimate(out.toString()) + tokens > totalBudget) {
                dropped.add(name + ":budget-exceeded");
                continue;
            }
            out.append(manual).append('\n');
            loaded.add(name);
            tokensBySkill.put(name, tokens);
        }
        if (loaded.isEmpty()) return SkillContextAssembly.empty(totalBudget);
        return new SkillContextAssembly(out.toString(), estimator.estimate(out.toString()),
                totalBudget, loaded, dropped, tokensBySkill);
    }

    private String renderManual(SkillMetadata metadata) {
        StringBuilder text = new StringBuilder("## Skill: ").append(metadata.name()).append('\n');
        if (metadata.description() != null) text.append("Purpose: ").append(metadata.description()).append('\n');
        if (!metadata.instructions().isBlank()) {
            text.append(metadata.instructions()).append('\n');
        } else if (!metadata.steps().isEmpty()) {
            text.append("Steps:\n");
            for (SkillStep step : metadata.steps()) {
                text.append("- ").append(step.name());
                if (step.uses() != null) text.append(" uses ").append(step.uses());
                if (step.description() != null) text.append(": ").append(step.description());
                text.append('\n');
            }
        }
        return text.toString();
    }
}
