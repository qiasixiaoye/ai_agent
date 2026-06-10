package com.vs.vsaiagent.agentplatform.service;

import com.vs.vsaiagent.agentplatform.model.TaskExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.TaskExecuteResult;

public interface TaskOrchestratorService {
    TaskExecuteResult execute(TaskExecuteRequest request);

    TaskExecuteResult runDemoFlow(String query);
}
