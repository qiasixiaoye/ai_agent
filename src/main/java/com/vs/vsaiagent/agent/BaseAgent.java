package com.vs.vsaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.vs.vsaiagent.agent.model.AgentState;
import com.vs.vsaiagent.observability.context.TraceContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for agents that manage their own multi-step state.
 */
@Data
@Slf4j
public abstract class BaseAgent {

    private String name;
    private String systemPrompt;
    private String nextStepPrompt;
    private AgentState state = AgentState.IDLE;
    private int currentStep = 0;
    private int maxSteps = 10;
    private ChatClient chatClient;
    private List<Message> messageList = new ArrayList<>();
    private final AtomicBoolean cleanedUp = new AtomicBoolean(false);

    public String run(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new IllegalStateException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new IllegalArgumentException("Cannot run agent with empty user prompt");
        }

        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && this.state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                log.info("Executing step: {}/{}", currentStep, maxSteps);
                results.add("Step " + currentStep + ": " + step());
            }
            if (currentStep >= maxSteps) {
                this.state = AgentState.FINISHED;
                results.add("Terminated: reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "Agent execution error: " + e.getMessage();
        } finally {
            cleanupOnce();
        }
    }

    /**
     * Runs the agent on a background thread and writes named typed SSE events.
     */
    public SseEmitter runStream(String userPrompt, String contentText) {
        if (StrUtil.isNotBlank(contentText)) {
            messageList.add(new UserMessage("Previous conversation history:\n" + contentText));
        }

        SseEmitter sseEmitter = new SseEmitter(300000L);
        ManusStreamEventEmitter eventEmitter = new ManusStreamEventEmitter(event ->
                sseEmitter.send(SseEmitter.event().name(event.type()).data(event)));

        CompletableFuture.runAsync(TraceContext.wrap(() -> {
            try {
                validateRunRequest(userPrompt);
                this.state = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));

                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    currentStep = i + 1;
                    log.info("Executing streamed step: {}/{}", currentStep, maxSteps);
                    if (streamStep(currentStep, eventEmitter)) {
                        state = AgentState.FINISHED;
                        break;
                    }
                }
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                }
                eventEmitter.complete(currentStep);
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing streamed agent", e);
                try {
                    eventEmitter.error(e.getMessage());
                    sseEmitter.complete();
                } catch (IOException sendFailure) {
                    sseEmitter.completeWithError(sendFailure);
                }
            } finally {
                cleanupOnce();
            }
        }));

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            cleanupOnce();
            log.warn("SSE connection timeout");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            cleanupOnce();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    private void validateRunRequest(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new IllegalStateException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new IllegalArgumentException("Cannot run agent with empty user prompt");
        }
    }

    private void cleanupOnce() {
        if (cleanedUp.compareAndSet(false, true)) {
            cleanup();
        }
    }

    public abstract String step();

    protected abstract boolean streamStep(int step, ManusStreamEventEmitter eventEmitter) throws Exception;

    protected void cleanup() {
    }
}
