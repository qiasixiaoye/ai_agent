package com.vs.vsaiagent.memory;

import java.util.List;
import java.util.Map;

public record ContextAssembly(
        String conversationId,
        String contextText,
        int estimatedTokens,
        int tokenBudget,
        boolean truncated,
        List<String> includedTiers,
        Map<String, Integer> tokensByTier,
        Map<String, Integer> itemsByTier,
        List<MemoryRecall> recalls,
        List<String> droppedReasons
) {
    public ContextAssembly {
        includedTiers = includedTiers == null ? List.of() : List.copyOf(includedTiers);
        tokensByTier = tokensByTier == null ? Map.of() : Map.copyOf(tokensByTier);
        itemsByTier = itemsByTier == null ? Map.of() : Map.copyOf(itemsByTier);
        recalls = recalls == null ? List.of() : List.copyOf(recalls);
        droppedReasons = droppedReasons == null ? List.of() : List.copyOf(droppedReasons);
    }
}
