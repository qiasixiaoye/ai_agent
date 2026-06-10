package com.vs.vsaiagent.agentplatform.registry;

import com.vs.vsaiagent.agentplatform.tool.AgentTool;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolRegistryInitializer {

    private final ToolRegistry toolRegistry;
    private final List<AgentTool> tools;

    public ToolRegistryInitializer(ToolRegistry toolRegistry, List<AgentTool> tools) {
        this.toolRegistry = toolRegistry;
        this.tools = tools;
    }

    @PostConstruct
    public void init() {
        for (AgentTool tool : tools) {
            toolRegistry.register(tool);
        }
    }
}
