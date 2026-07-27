package com.vs.vsaiagent.agent;

import com.vs.vsaiagent.agent.model.ManusStreamEvent;

import java.io.IOException;

@FunctionalInterface
public interface ManusStreamEventListener {

    void onEvent(ManusStreamEvent event) throws IOException;
}
