package com.vs.vsaiagent.mcp.management;

public record McpToolCallResult(
        String toolName,
        boolean success,
        String output,
        String errorMessage,
        long costMs,
        String circuitState
) {
}
