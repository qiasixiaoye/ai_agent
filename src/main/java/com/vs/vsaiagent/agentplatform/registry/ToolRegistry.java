package com.vs.vsaiagent.agentplatform.registry;

import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.tool.AgentTool;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    void register(AgentTool tool);

    Optional<AgentTool> findByName(String toolName);

    List<AgentTool> searchByTag(String tag);

    List<ToolMetadata> listMetadata();
}
