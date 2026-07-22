package com.vs.vsaiagent.context;

import com.vs.vsaiagent.mcp.management.ManagedMcpToolService;
import com.vs.vsaiagent.memory.ContextAssembly;
import com.vs.vsaiagent.memory.ContextWindowManager;
import com.vs.vsaiagent.memory.HierarchicalChatMemory;
import com.vs.vsaiagent.observability.vo.ApiResponse;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.adapter.SkillCallbackAdapter;
import com.vs.vsaiagent.skill.context.SkillContextAssembly;
import com.vs.vsaiagent.skill.context.SkillContextManager;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import com.vs.vsaiagent.skill.routing.SkillRouteDecision;
import com.vs.vsaiagent.skill.routing.SkillRouter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** A /context-like view that explains what consumes the configured model window. */
@RestController
@RequestMapping("/context-management")
public class ContextManagementController {

    private static final String BASE_SYSTEM = "general assistant system prompt";

    private final ContextBudgetManager budgetManager;
    private final ContextWindowManager windowManager;
    private final HierarchicalChatMemory memory;
    private final SkillRouter skillRouter;
    private final SkillRegistry skillRegistry;
    private final SkillContextManager skillContextManager;
    private final ManagedMcpToolService mcpToolService;

    public ContextManagementController(ContextBudgetManager budgetManager,
                                       ContextWindowManager windowManager,
                                       HierarchicalChatMemory memory,
                                       SkillRouter skillRouter,
                                       SkillRegistry skillRegistry,
                                       SkillContextManager skillContextManager,
                                       ManagedMcpToolService mcpToolService) {
        this.budgetManager = budgetManager;
        this.windowManager = windowManager;
        this.memory = memory;
        this.skillRouter = skillRouter;
        this.skillRegistry = skillRegistry;
        this.skillContextManager = skillContextManager;
        this.mcpToolService = mcpToolService;
    }

    @GetMapping("/diagnostics")
    public ApiResponse<ContextDiagnostics> diagnostics(
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "plain") String mode) {
        ToolCallback[] callbacks = new ToolCallback[0];
        SkillContextAssembly skillContext = SkillContextAssembly.empty(
                budgetManager.cap(ContextBudgetManager.SKILLS));

        if ("skills".equalsIgnoreCase(mode)) {
            SkillRouteDecision decision = skillRouter.route(query);
            skillContext = skillContextManager.assemble(decision);
            callbacks = decision.selectedSkillNames().stream()
                    .map(name -> skillRegistry.find(name).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .map(skill -> (ToolCallback) new SkillCallbackAdapter((Skill) skill))
                    .toArray(ToolCallback[]::new);
        } else if ("mcp".equalsIgnoreCase(mode)) {
            callbacks = mcpToolService.allowedToolCallbacks(query);
        }

        ContextBudgetPlan plan = budgetManager.plan(BASE_SYSTEM, query,
                skillContext.contextText(), callbacks);
        ContextAssembly memory = windowManager.assemble(conversationId, query, plan);
        List<String> tools = java.util.Arrays.stream(callbacks).map(ToolCallback::getName).toList();
        return ApiResponse.success(new ContextDiagnostics(mode, plan,
                this.memory.historyUsage(conversationId, 10), memory, skillContext, tools));
    }
}
