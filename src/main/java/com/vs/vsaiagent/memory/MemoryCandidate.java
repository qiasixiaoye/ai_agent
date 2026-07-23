package com.vs.vsaiagent.memory;

public record MemoryCandidate(
        String type,
        String content,
        double importance,
        double confidence,
        String sensitivity,
        String dedupeKey,
        String status) {
}
