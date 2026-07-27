package com.vs.vsaiagent.observability.service;

import com.vs.vsaiagent.observability.entity.AgentStageLogEntity;
import com.vs.vsaiagent.observability.repository.AgentStageLogRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageLogBufferTest {

    @Test
    void batchesAcceptedEventsAndDropsEventsAfterTheBoundedQueueIsFull() {
        RecordingRepository repository = new RecordingRepository();
        StageLogBuffer buffer = new StageLogBuffer(repository, 2, 10);

        assertTrue(buffer.offer(event("one")));
        assertTrue(buffer.offer(event("two")));
        assertFalse(buffer.offer(event("three")));

        buffer.flush();

        assertEquals(List.of("one", "two"), repository.batches.get(0).stream()
                .map(AgentStageLogEntity::getStageName).toList());
    }

    @Test
    void keepsTheRequestPathAvailableWhenBatchPersistenceFails() {
        StageLogBuffer buffer = new StageLogBuffer(new FailingRepository(), 2, 10);
        assertTrue(buffer.offer(event("will-drop-on-db-failure")));

        assertDoesNotThrow(buffer::flush);
    }

    private static AgentStageLogEntity event(String name) {
        return AgentStageLogEntity.builder().stageName(name).build();
    }

    private static final class RecordingRepository extends AgentStageLogRepository {
        private final List<List<AgentStageLogEntity>> batches = new ArrayList<>();

        private RecordingRepository() {
            super(null);
        }

        @Override
        public void batchInsert(List<AgentStageLogEntity> entities) {
            batches.add(List.copyOf(entities));
        }
    }

    private static final class FailingRepository extends AgentStageLogRepository {

        private FailingRepository() {
            super(null);
        }

        @Override
        public void batchInsert(List<AgentStageLogEntity> entities) {
            throw new IllegalStateException("database is unavailable");
        }
    }
}
