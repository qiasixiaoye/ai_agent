package com.vs.vsaiagent.agent;

import com.vs.vsaiagent.agent.model.ManusStreamEvent;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ensures a Manus stream has at most one terminal event.
 */
public class ManusStreamEventEmitter {

    private final ManusStreamEventListener listener;
    private final AtomicBoolean terminal = new AtomicBoolean(false);

    public ManusStreamEventEmitter(ManusStreamEventListener listener) {
        this.listener = listener;
    }

    public void emit(ManusStreamEvent event) throws IOException {
        if (!terminal.get()) {
            listener.onEvent(event);
        }
    }

    public void complete(int steps) throws IOException {
        emitTerminal(ManusStreamEvent.complete(steps));
    }

    public void error(String message) throws IOException {
        emitTerminal(ManusStreamEvent.error(message));
    }

    private void emitTerminal(ManusStreamEvent event) throws IOException {
        if (terminal.compareAndSet(false, true)) {
            listener.onEvent(event);
        }
    }
}
