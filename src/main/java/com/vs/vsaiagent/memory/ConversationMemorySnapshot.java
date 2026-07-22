package com.vs.vsaiagent.memory;

import java.util.List;

public record ConversationMemorySnapshot(
        String conversationId,
        int workingMessageCount,
        String rollingSummary,
        List<MemoryItem> semanticMemories,
        List<MemoryItem> episodicMemories,
        long updatedAt
) {
}
