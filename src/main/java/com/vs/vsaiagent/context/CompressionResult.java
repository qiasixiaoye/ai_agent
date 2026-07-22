package com.vs.vsaiagent.context;

public record CompressionResult(
        String content,
        int originalTokens,
        int retainedTokens,
        boolean compressed,
        String strategy
) {
}
