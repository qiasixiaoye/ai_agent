package com.vs.vsaiagent.agent;

import com.vs.vsaiagent.agent.model.ManusStreamEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManusStreamEventEmitterTest {

    @Test
    void forwardsNamedThinkingPayloadToItsConsumer() throws IOException {
        List<ManusStreamEvent> events = new ArrayList<>();
        ManusStreamEventEmitter emitter = new ManusStreamEventEmitter(events::add);

        emitter.emit(ManusStreamEvent.thinking(2, "plan"));

        assertEquals(List.of(new ManusStreamEvent("thinking", 2, "plan", null, null, null, null)), events);
    }

    @Test
    void acceptsOnlyTheFirstTerminalEvent() throws IOException {
        List<ManusStreamEvent> events = new ArrayList<>();
        ManusStreamEventEmitter emitter = new ManusStreamEventEmitter(events::add);

        emitter.complete(3);
        emitter.error("ignored");

        assertEquals(List.of(new ManusStreamEvent("complete", 3, null, null, null, null, null)), events);
    }
}
