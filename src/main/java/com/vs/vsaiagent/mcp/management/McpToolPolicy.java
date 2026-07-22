package com.vs.vsaiagent.mcp.management;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** MCP 工具的 allow/deny 与高风险确认策略。 */
@Component
public class McpToolPolicy {

    private final List<String> allowPatterns;
    private final List<String> denyPatterns;
    private final List<String> highRiskPatterns;

    public McpToolPolicy(
            @Value("${app.mcp.policy.allow:}") String allow,
            @Value("${app.mcp.policy.deny:terminal,shell,execute_command,run_command}") String deny,
            @Value("${app.mcp.policy.high-risk:delete,remove,write,send,publish,payment,deploy}") String highRisk) {
        this.allowPatterns = split(allow);
        this.denyPatterns = split(deny);
        this.highRiskPatterns = split(highRisk);
    }

    public McpPolicyDecision evaluate(String toolName, boolean confirmed) {
        String name = normalize(toolName);
        if (name.isBlank()) return new McpPolicyDecision(false, false, McpRiskLevel.HIGH, "tool name is blank");
        if (matches(name, denyPatterns)) {
            return new McpPolicyDecision(false, false, McpRiskLevel.HIGH, "blocked by deny policy");
        }
        if (!allowPatterns.isEmpty() && !matches(name, allowPatterns)) {
            return new McpPolicyDecision(false, false, McpRiskLevel.MEDIUM, "not included in allow policy");
        }
        if (matches(name, highRiskPatterns)) {
            return new McpPolicyDecision(confirmed, !confirmed, McpRiskLevel.HIGH,
                    confirmed ? "high-risk operation confirmed" : "high-risk operation requires confirmation");
        }
        McpRiskLevel risk = containsAny(name, "create", "update", "upload", "download")
                ? McpRiskLevel.MEDIUM : McpRiskLevel.LOW;
        return new McpPolicyDecision(true, false, risk, "allowed");
    }

    private static boolean matches(String name, List<String> patterns) {
        return patterns.stream().anyMatch(name::contains);
    }

    private static boolean containsAny(String value, String... candidates) {
        return Arrays.stream(candidates).anyMatch(value::contains);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(McpToolPolicy::normalize).filter(s -> !s.isBlank()).toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
