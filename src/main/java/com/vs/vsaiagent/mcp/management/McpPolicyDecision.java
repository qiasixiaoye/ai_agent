package com.vs.vsaiagent.mcp.management;

public record McpPolicyDecision(boolean allowed, boolean confirmationRequired,
                                McpRiskLevel riskLevel, String reason) {
}
