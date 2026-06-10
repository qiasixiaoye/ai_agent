package com.vs.vsaiagent.agentplatform.tool;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;

public interface AgentTool {
    ToolMetadata metadata();

    default String toolName() {
        return metadata().getToolName();
    }

    void validate(ToolExecuteRequest request);

    ToolExecuteResult execute(ToolExecuteRequest request);
}
