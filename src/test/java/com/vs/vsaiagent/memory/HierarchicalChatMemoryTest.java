package com.vs.vsaiagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchicalChatMemoryTest {

    @TempDir
    Path tempDir;

    @Test
    void compactsWorkingMemoryAndPersistsSummaryAndFacts() {
        HierarchicalChatMemory memory = memory();
        String id = "../../unsafe/session";
        memory.add(id, List.of(new UserMessage("我叫小林，我喜欢浅烘咖啡")));
        memory.add(id, List.of(new AssistantMessage("收到")));
        memory.add(id, List.of(new UserMessage("第一轮任务")));
        memory.add(id, List.of(new AssistantMessage("第一轮完成")));
        memory.add(id, List.of(new UserMessage("第二轮任务")));
        memory.add(id, List.of(new AssistantMessage("第二轮完成")));

        ConversationMemorySnapshot snapshot = memory.snapshot(id);
        assertEquals(4, snapshot.workingMessageCount());
        assertFalse(snapshot.rollingSummary().isBlank());
        assertTrue(snapshot.semanticMemories().stream().anyMatch(item -> item.content().contains("小林")));
        assertTrue(snapshot.semanticMemories().stream().anyMatch(item -> item.content().contains("浅烘咖啡")));

        HierarchicalChatMemory reloaded = memory();
        assertEquals(4, reloaded.get(id, 20).size());
        assertEquals(snapshot.rollingSummary(), reloaded.snapshot(id).rollingSummary());
    }

    @Test
    void retrievesSemanticAndEpisodicMemoryWithinContextBudget() {
        HierarchicalChatMemory memory = memory();
        memory.rememberSemantic("c1", "用户偏好浅烘咖啡和手冲", 0.9, Map.of("source", "manual"));
        memory.rememberSemantic("c1", "用户的主要开发语言是Java", 0.8, Map.of());
        memory.rememberEpisode("c1", "上次使用coffee工具生成了耶加雪菲冲煮建议", 0.7, Map.of("tool", "coffee"));

        ContextAssembly assembly = new ContextWindowManager(memory, 200, 3, 2)
                .assemble("c1", "继续聊浅烘咖啡", 200);

        assertTrue(assembly.contextText().contains("浅烘咖啡"));
        assertTrue(assembly.contextText().contains("coffee工具"));
        assertTrue(assembly.estimatedTokens() <= 230);
        assertTrue(assembly.includedTiers().contains("SEMANTIC"));
        assertTrue(assembly.includedTiers().contains("EPISODIC"));
    }

    @Test
    void clearsAllConversationTiers() {
        HierarchicalChatMemory memory = memory();
        memory.add("c2", List.of(new UserMessage("hello")));
        memory.rememberEpisode("c2", "tool output", 0.5, Map.of());
        memory.clear("c2");

        ConversationMemorySnapshot snapshot = memory.snapshot("c2");
        assertEquals(0, snapshot.workingMessageCount());
        assertTrue(snapshot.episodicMemories().isEmpty());
    }

    @Test
    void compactsOnTokenPressurePersistsAccessAndSupportsSelectiveForget() {
        HierarchicalChatMemory memory = new HierarchicalChatMemory(
                tempDir.toString(), 20, 1000, 20, 200);
        for (int i = 0; i < 6; i++) {
            memory.add("token-session", List.of(new UserMessage("消息" + i + "-" + "长内容".repeat(80))));
        }
        assertFalse(memory.snapshot("token-session").rollingSummary().isBlank());
        assertTrue(memory.historyUsage("token-session", 10).estimatedTokens() <= 200);

        memory.rememberSemantic("token-session", "user prefers Java", 0.9,
                Map.of("memoryKey", "preferred-language"));
        MemoryItem item = memory.search("token-session", MemoryTier.SEMANTIC, "Java", 1).getFirst();
        MemoryItem persisted = memory.snapshot("token-session").semanticMemories().getFirst();
        assertEquals(1, persisted.accessCount());
        assertTrue(memory.forget("token-session", item.id()));
        assertTrue(memory.snapshot("token-session").semanticMemories().isEmpty());
    }

    private HierarchicalChatMemory memory() {
        return new HierarchicalChatMemory(tempDir.toString(), 4, 1000, 20);
    }
}
