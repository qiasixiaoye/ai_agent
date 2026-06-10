package com.vs.vsaiagent.agentplatform.service;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;

import java.util.List;

public interface ToolExecutionService {
    ToolExecuteResult executeByName(ToolExecuteRequest request);

    ToolExecuteResult executeByMetadata(String tag, ToolExecuteRequest request);

    List<ToolMetadata> listTools();
}
