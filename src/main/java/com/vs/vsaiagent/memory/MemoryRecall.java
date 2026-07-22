package com.vs.vsaiagent.memory;

import java.util.List;

/** A recall result with enough evidence to explain why it entered the prompt. */
public record MemoryRecall(
        MemoryItem item,
        double score,
        double relevance,
        List<String> reasons
) {
    public MemoryRecall {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
