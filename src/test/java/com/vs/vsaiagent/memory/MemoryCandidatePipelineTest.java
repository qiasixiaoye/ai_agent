package com.vs.vsaiagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCandidatePipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void writesStablePreferenceButDoesNotPersistRawToolOutput() {
        HierarchicalChatMemory memory = new HierarchicalChatMemory(tempDir.toString(), 8, 1000, 20);
        MemoryCandidatePipeline pipeline = new MemoryCandidatePipeline(memory);

        List<MemoryCandidate> candidates = pipeline.onTurnCompleted(new MemoryCandidatePipeline.CompletedTurn(
                "c1", "我喜欢清淡的咖啡", "好的，我会记住你的偏好。", List.of()));

        assertEquals(1, candidates.size());
        assertEquals("written", candidates.getFirst().status());
        assertTrue(memory.snapshot("c1").semanticMemories().stream()
                .anyMatch(item -> item.content().contains("我喜欢清淡的咖啡")));

        List<MemoryCandidate> toolCandidates = pipeline.onTurnCompleted(new MemoryCandidatePipeline.CompletedTurn(
                "c1", "查询天气", "Step 1: 工具:searchWeb返回结果: No web results", List.of()));
        assertTrue(toolCandidates.isEmpty());
    }

    @Test
    void marksSensitiveCandidatePendingAndUsesDedupeKey() {
        HierarchicalChatMemory memory = new HierarchicalChatMemory(tempDir.toString(), 8, 1000, 20);
        MemoryCandidatePipeline pipeline = new MemoryCandidatePipeline(memory);

        List<MemoryCandidate> first = pipeline.onTurnCompleted(new MemoryCandidatePipeline.CompletedTurn(
                "c2", "我的密码是 abc123", "收到。", List.of()));
        List<MemoryCandidate> second = pipeline.onTurnCompleted(new MemoryCandidatePipeline.CompletedTurn(
                "c2", "我喜欢清淡的咖啡", "收到。", List.of()));
        List<MemoryCandidate> duplicate = pipeline.onTurnCompleted(new MemoryCandidatePipeline.CompletedTurn(
                "c2", "我喜欢清淡的咖啡", "收到。", List.of()));

        assertEquals("pending", first.getFirst().status());
        assertEquals(1, second.size());
        assertTrue(duplicate.isEmpty());
    }
}
