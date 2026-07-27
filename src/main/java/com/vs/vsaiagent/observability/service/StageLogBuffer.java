package com.vs.vsaiagent.observability.service;

import com.vs.vsaiagent.observability.entity.AgentStageLogEntity;
import com.vs.vsaiagent.observability.repository.AgentStageLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

@Component
@Slf4j
public class StageLogBuffer {

    private final AgentStageLogRepository repository;
    private final ArrayBlockingQueue<AgentStageLogEntity> queue;
    private final int batchSize;

    public StageLogBuffer(AgentStageLogRepository repository,
                          @Value("${app.observability.stage-log.queue-capacity:1000}") int queueCapacity,
                          @Value("${app.observability.stage-log.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
    }

    public boolean offer(AgentStageLogEntity entity) {
        boolean accepted = queue.offer(entity);
        if (!accepted) {
            log.warn("Dropping stage log because the async buffer is full, requestId={}, stage={}",
                    entity.getRequestId(), entity.getStageName());
        }
        return accepted;
    }

    @Scheduled(fixedDelayString = "${app.observability.stage-log.flush-delay-ms:500}")
    public void flush() {
        List<AgentStageLogEntity> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        if (batch.isEmpty()) {
            return;
        }
        try {
            repository.batchInsert(batch);
        } catch (RuntimeException e) {
            log.warn("Dropping {} stage logs after batch persistence failed", batch.size(), e);
        }
    }
}
