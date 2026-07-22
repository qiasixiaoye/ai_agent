package com.vs.vsaiagent.context;

import com.vs.vsaiagent.memory.ContextAssembly;
import com.vs.vsaiagent.memory.HistoryUsage;
import com.vs.vsaiagent.skill.context.SkillContextAssembly;

import java.util.List;

public record ContextDiagnostics(
        String mode,
        ContextBudgetPlan budgetPlan,
        HistoryUsage history,
        ContextAssembly memoryContext,
        SkillContextAssembly skillContext,
        List<String> exposedTools
) {
    public ContextDiagnostics {
        exposedTools = exposedTools == null ? List.of() : List.copyOf(exposedTools);
    }
}
