package com.vs.vsaiagent.orchestration;

import com.vs.vsaiagent.app.AssistantApp;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestOrchestratorTest {

    @Test
    void emitsTraceableFinalAnswerEventsWithoutStepText() {
        AssistantApp assistantApp = mock(AssistantApp.class);
        when(assistantApp.doChatByStream("hello", "conversation-1"))
                .thenReturn(Flux.just("答", "案"));
        RequestOrchestrator orchestrator = new RequestOrchestrator(assistantApp, new CapabilityRouter());

        List<OrchestrationEvent> events = orchestrator.stream(
                        new OrchestrationRequest("conversation-1", "hello", null, "request-1", "trace-1"))
                .map(event -> event.data())
                .collectList()
                .block();

        assertEquals(List.of("request_started", "route_selected", "final_delta", "final_delta", "final_completed"),
                events.stream().map(OrchestrationEvent::type).toList());
        assertEquals("答", events.get(2).payload().get("text"));
        assertEquals("案", events.get(3).payload().get("text"));
        assertFalse(events.get(4).payload().containsKey("rawToolOutput"));
    }
}
