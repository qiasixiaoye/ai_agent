package com.vs.vsaiagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolCallAgentRetryTest {

    @Test
    void retriesTransientModelFailuresBeforeReturningTheModelResponse() {
        RetryingToolCallAgent agent = new RetryingToolCallAgent(1,
                new IllegalStateException("HTTP 429 rate limit"));

        assertFalse(agent.think());
        assertEquals(2, agent.calls.get());
        assertEquals("recovered", agent.getToolCallChatResponse().getResult().getOutput().getText());
    }

    @Test
    void propagatesNonTransientModelFailuresInsteadOfReportingTaskCompletion() {
        RetryingToolCallAgent agent = new RetryingToolCallAgent(Integer.MAX_VALUE,
                new IllegalArgumentException("invalid tool schema"));

        assertThrows(IllegalArgumentException.class, agent::think);
        assertEquals(1, agent.calls.get());
    }

    private static final class RetryingToolCallAgent extends ToolCallAgent {
        private final int failuresBeforeSuccess;
        private final RuntimeException failure;
        private final AtomicInteger calls = new AtomicInteger();

        private RetryingToolCallAgent(int failuresBeforeSuccess, RuntimeException failure) {
            super(new ToolCallback[0]);
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            this.failure = failure;
            setNextStepPrompt("");
        }

        @Override
        protected ChatResponse callPrompt(Prompt prompt) {
            if (calls.getAndIncrement() < failuresBeforeSuccess) {
                throw failure;
            }
            return new ChatResponse(java.util.List.of(new Generation(new AssistantMessage("recovered"))));
        }

        @Override
        protected void sleepBeforeRetry(int retryAttempt) {
        }
    }
}
