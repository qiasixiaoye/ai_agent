package com.vs.vsaiagent.orchestration;

import java.util.List;
import java.util.Locale;

/** Deterministic first gate for the unified request entry point. */
public class CapabilityRouter {

    private static final List<String> KNOWLEDGE_HINTS = List.of(
            "知识库", "项目资料", "文档", "资料", "根据文件", "according to the project", "from the docs");
    private static final List<String> TOOL_HINTS = List.of(
            "查询", "搜索", "天气", "汇率", "计算", "现在", "今天", "search", "weather", "calculate");
    private static final List<String> EXECUTION_HINTS = List.of(
            "执行", "创建", "删除", "发送", "发布", "部署", "运行", "规划并", "workflow", "execute", "deploy");

    public RoutePlan route(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean knowledge = containsAny(normalized, KNOWLEDGE_HINTS);
        boolean tool = containsAny(normalized, TOOL_HINTS);
        boolean execution = containsAny(normalized, EXECUTION_HINTS);
        if (execution && (knowledge || tool || normalized.contains("规划"))) {
            return new RoutePlan(RoutePlan.Route.MIXED, List.of(), false);
        }
        if (execution) {
            return new RoutePlan(RoutePlan.Route.SKILL, List.of(), false);
        }
        if (knowledge && tool) {
            return new RoutePlan(RoutePlan.Route.MIXED, List.of(), false);
        }
        if (knowledge) {
            return new RoutePlan(RoutePlan.Route.KNOWLEDGE, List.of(), false);
        }
        if (tool) {
            return new RoutePlan(RoutePlan.Route.TOOL, List.of(), false);
        }
        return new RoutePlan(RoutePlan.Route.DIRECT, List.of(), false);
    }

    private boolean containsAny(String text, List<String> hints) {
        return hints.stream().anyMatch(text::contains);
    }
}
