package com.vs.vsaiagent.agent;

import com.vs.vsaiagent.agent.model.ManusStreamEvent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallAgentStreamingTest {

    @Test
    void emitsEachTextDeltaAsItArrivesInsteadOfWaitingForTheCompleteResponse() throws IOException {
        TestToolCallAgent agent = new TestToolCallAgent(Flux.just(response("first "), response("token")));
        List<ManusStreamEvent> events = new ArrayList<>();

        boolean requiresTool = agent.thinkStream(1, new ManusStreamEventEmitter(events::add));

        assertFalse(requiresTool);
        assertEquals(List.of(
                ManusStreamEvent.thinking(1, "first "),
                ManusStreamEvent.thinking(1, "token")), events);
        assertEquals("first token", agent.getToolCallChatResponse().getResult().getOutput().getText());
    }

    @Test
    void preservesToolCallsFromTheCompleteStreamForTheExistingActMethod() throws IOException {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call-1", "function", "web_search", "{\"q\":\"Spring AI\"}");
        TestToolCallAgent agent = new TestToolCallAgent(Flux.just(responseWithToolCall(toolCall)));

        boolean requiresTool = agent.thinkStream(1, new ManusStreamEventEmitter(event -> { }));

        assertTrue(requiresTool);
        assertEquals(List.of(toolCall), agent.getToolCallChatResponse().getResult().getOutput().getToolCalls());
    }

    @Test
    void emitsAnswerOnlyAfterTheThinkingDeltasWhenNoToolIsRequired() throws IOException {
        TestToolCallAgent agent = new TestToolCallAgent(Flux.just(response("final answer")));
        List<ManusStreamEvent> events = new ArrayList<>();

        boolean finished = agent.streamStep(1, new ManusStreamEventEmitter(events::add));

        assertTrue(finished);
        assertEquals(List.of(
                ManusStreamEvent.thinking(1, "final answer"),
                ManusStreamEvent.answer(1, "final answer")), events);
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse responseWithToolCall(AssistantMessage.ToolCall toolCall) {
        AssistantMessage message = new AssistantMessage("", Map.of(), List.of(toolCall));
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static final class TestToolCallAgent extends ToolCallAgent {

        private final Flux<ChatResponse> responses;

        private TestToolCallAgent(Flux<ChatResponse> responses) {
            super(new ToolCallback[0]);
            this.responses = responses;
            setNextStepPrompt("");
        }

        @Override
        protected Flux<ChatResponse> streamPrompt(Prompt prompt) {
            return responses;
        }
    }
}
