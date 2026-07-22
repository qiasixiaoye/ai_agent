package com.vs.vsaiagent.mcp.management;

public record McpToolDescriptor(
        String name,
        String description,
        String inputSchema,
        boolean enabled,
        McpRiskLevel riskLevel,
        boolean confirmationRequired,
        String circuitState,
        int consecutiveFailures,
        long circuitOpenUntil
) {
}
