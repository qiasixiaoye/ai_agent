package com.vs.vsaiagent.mcp.management;

public record McpRuntimeHealth(
        boolean providerAvailable,
        int discoveredTools,
        int enabledTools,
        int openCircuits,
        long catalogRefreshedAt
) {
}
