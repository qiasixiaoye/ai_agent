package com.vs.vsaiagent.agent.model;

/**
 * A named event emitted by the Manus SSE endpoint.
 */
public record ManusStreamEvent(
        String type,
        int step,
        String text,
        String toolName,
        String toolArguments,
        String toolResult,
        String message) {

    public static ManusStreamEvent thinking(int step, String text) {
        return new ManusStreamEvent("thinking", step, text, null, null, null, null);
    }

    public static ManusStreamEvent toolCall(int step, String toolName, String toolArguments) {
        return new ManusStreamEvent("tool_call", step, null, toolName, toolArguments, null, null);
    }

    public static ManusStreamEvent toolResult(int step, String toolName, String toolResult) {
        return new ManusStreamEvent("tool_result", step, null, toolName, null, toolResult, null);
    }

    public static ManusStreamEvent answer(int step, String text) {
        return new ManusStreamEvent("answer", step, text, null, null, null, null);
    }

    public static ManusStreamEvent complete(int steps) {
        return new ManusStreamEvent("complete", steps, null, null, null, null, null);
    }

    public static ManusStreamEvent error(String message) {
        return new ManusStreamEvent("error", 0, null, null, null, null, message);
    }
}
