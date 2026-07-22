package com.vs.vsaiagent.memory;

import java.util.Map;

public record MemoryItem(
        String id,
        MemoryTier tier,
        String content,
        double importance,
        long createdAt,
        long lastAccessAt,
        int accessCount,
        Map<String, String> metadata
) {
    public MemoryItem {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
