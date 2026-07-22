package com.vs.vsaiagent.context;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Central policy for what may occupy the model input window. */
@Component
public class ContextBudgetManager {

    public static final String HISTORY = "history";
    public static final String SUMMARY = "summary";
    public static final String SEMANTIC = "semantic";
    public static final String EPISODIC = "episodic";
    public static final String RAG = "rag";
    public static final String SKILLS = "skills";
    public static final String TOOL_SCHEMAS = "toolSchemas";
    public static final String TOOL_RESULT = "toolResult";

    private final TokenEstimator estimator;
    private final int contextWindow;
    private final int outputReserve;
    private final int safetyMargin;
    private final Map<String, Integer> caps;

    public ContextBudgetManager(TokenEstimator estimator,
                                @Value("${app.context.window-tokens:32768}") int contextWindow,
                                @Value("${app.context.output-reserve-tokens:4096}") int outputReserve,
                                @Value("${app.context.safety-margin-tokens:2048}") int safetyMargin,
                                @Value("${app.context.history-token-budget:5000}") int history,
                                @Value("${app.context.summary-token-budget:1500}") int summary,
                                @Value("${app.context.semantic-token-budget:1000}") int semantic,
                                @Value("${app.context.episodic-token-budget:1000}") int episodic,
                                @Value("${app.context.rag-token-budget:5000}") int rag,
                                @Value("${app.context.skill-token-budget:3500}") int skills,
                                @Value("${app.context.tool-schema-token-budget:2000}") int toolSchemas,
                                @Value("${app.context.tool-result-token-budget:3500}") int toolResult) {
        this.estimator = estimator;
        this.contextWindow = Math.max(4096, contextWindow);
        this.outputReserve = Math.max(256, outputReserve);
        this.safetyMargin = Math.max(128, safetyMargin);
        LinkedHashMap<String, Integer> configured = new LinkedHashMap<>();
        configured.put(HISTORY, positive(history));
        configured.put(SUMMARY, positive(summary));
        configured.put(SEMANTIC, positive(semantic));
        configured.put(EPISODIC, positive(episodic));
        configured.put(RAG, positive(rag));
        configured.put(SKILLS, positive(skills));
        configured.put(TOOL_SCHEMAS, positive(toolSchemas));
        configured.put(TOOL_RESULT, positive(toolResult));
        this.caps = Map.copyOf(configured);
    }

    public ContextBudgetPlan plan(String systemPrompt, String userInput,
                                  String loadedSkillContext, ToolCallback[] tools) {
        LinkedHashMap<String, Integer> fixed = new LinkedHashMap<>();
        fixed.put("systemPrompt", estimator.estimate(systemPrompt));
        fixed.put("userInput", estimator.estimate(userInput));
        fixed.put(SKILLS, estimator.estimate(loadedSkillContext));
        fixed.put(TOOL_SCHEMAS, estimateToolSchemas(tools));
        int fixedTotal = fixed.values().stream().mapToInt(Integer::intValue).sum();
        int maxInput = Math.max(512, contextWindow - outputReserve - safetyMargin);
        int allocatable = Math.max(0, maxInput - fixedTotal);

        LinkedHashMap<String, Integer> budgets = new LinkedHashMap<>();
        budgets.put(HISTORY, caps.get(HISTORY));
        budgets.put(SUMMARY, caps.get(SUMMARY));
        budgets.put(SEMANTIC, caps.get(SEMANTIC));
        budgets.put(EPISODIC, caps.get(EPISODIC));
        budgets.put(RAG, caps.get(RAG));
        budgets.put(SKILLS, Math.min(caps.get(SKILLS), Math.max(0, allocatable)));
        budgets.put(TOOL_SCHEMAS, caps.get(TOOL_SCHEMAS));
        budgets.put(TOOL_RESULT, caps.get(TOOL_RESULT));

        int requestedDynamic = budgets.get(HISTORY) + budgets.get(SUMMARY)
                + budgets.get(SEMANTIC) + budgets.get(EPISODIC) + budgets.get(RAG);
        if (requestedDynamic > allocatable) {
            shrinkDynamicBudgets(budgets, allocatable, requestedDynamic);
        }
        int dynamic = budgets.get(HISTORY) + budgets.get(SUMMARY)
                + budgets.get(SEMANTIC) + budgets.get(EPISODIC) + budgets.get(RAG);
        int estimatedTotal = fixedTotal + dynamic;
        double utilization = Math.min(1.0, estimatedTotal / (double) maxInput);
        String pressure = utilization >= 0.90 ? "CRITICAL" : utilization >= 0.75 ? "HIGH" : "NORMAL";
        return new ContextBudgetPlan(contextWindow, outputReserve, safetyMargin, maxInput,
                fixedTotal, allocatable, estimatedTotal, round(utilization), pressure, fixed, budgets);
    }

    public int cap(String category) {
        return caps.getOrDefault(category, 0);
    }

    public String fit(String category, String content) {
        return estimator.fit(content, cap(category));
    }

    private int estimateToolSchemas(ToolCallback[] tools) {
        if (tools == null) return 0;
        int tokens = 0;
        for (ToolCallback tool : tools) {
            if (tool == null) continue;
            tokens += estimator.estimate(tool.getName())
                    + estimator.estimate(tool.getDescription())
                    + estimator.estimate(tool.getInputTypeSchema()) + 8;
        }
        return tokens;
    }

    private void shrinkDynamicBudgets(Map<String, Integer> budgets, int available, int requested) {
        if (available <= 0) {
            budgets.put(HISTORY, 0);
            budgets.put(SUMMARY, 0);
            budgets.put(SEMANTIC, 0);
            budgets.put(EPISODIC, 0);
            budgets.put(RAG, 0);
            return;
        }
        double scale = available / (double) requested;
        int used = 0;
        String[] categories = {HISTORY, SUMMARY, SEMANTIC, EPISODIC, RAG};
        for (int i = 0; i < categories.length; i++) {
            String category = categories[i];
            int value = i == categories.length - 1
                    ? Math.max(0, available - used)
                    : (int) Math.floor(budgets.get(category) * scale);
            budgets.put(category, value);
            used += value;
        }
    }

    private static int positive(int value) { return Math.max(0, value); }
    private static double round(double value) { return Math.round(value * 10_000.0) / 10_000.0; }
}
