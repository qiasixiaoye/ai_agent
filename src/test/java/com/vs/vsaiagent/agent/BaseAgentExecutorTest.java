package com.vs.vsaiagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseAgentExecutorTest {
    @Test
    void streamsWithTheConfiguredExecutorInsteadOfTheCommonPool() {
        AtomicInteger executions = new AtomicInteger();
        Executor executor = command -> { executions.incrementAndGet(); command.run(); };
        TestAgent agent = new TestAgent();
        agent.setExecutionExecutor(executor);

        SseEmitter ignored = agent.runStream("hello");

        assertEquals(1, executions.get());
    }

    private static final class TestAgent extends BaseAgent {
        @Override public String step() { return ""; }
        @Override protected boolean streamStep(int step, ManusStreamEventEmitter emitter) { return true; }
    }
}
