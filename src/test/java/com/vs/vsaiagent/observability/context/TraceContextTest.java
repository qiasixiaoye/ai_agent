package com.vs.vsaiagent.observability.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceContextTest {

    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    @Test
    void carriesTheSubmittingThreadTraceIntoAnAsyncTaskWithoutLeakingIt() throws InterruptedException {
        TraceInfo trace = new TraceInfo("trace-1", "request-1", "session-1");
        TraceContext.set(trace);
        AtomicReference<TraceInfo> observed = new AtomicReference<>();
        AtomicReference<TraceInfo> afterTask = new AtomicReference<>();

        Thread worker = new Thread(TraceContext.wrap(() -> observed.set(TraceContext.get())));
        worker.start();
        worker.join();

        Thread cleanWorker = new Thread(() -> afterTask.set(TraceContext.get()));
        cleanWorker.start();
        cleanWorker.join();

        assertEquals(trace, observed.get());
        assertNull(afterTask.get());
    }
}
