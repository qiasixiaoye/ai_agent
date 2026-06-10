package com.vs.vsaiagent.agentplatform.controller;

import com.vs.vsaiagent.agentplatform.dto.DemoTaskRequest;
import com.vs.vsaiagent.agentplatform.dto.ToolInvokeRequest;
import com.vs.vsaiagent.agentplatform.model.TaskExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.TaskExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.service.TaskOrchestratorService;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import com.vs.vsaiagent.agentplatform.vo.AgentApiResponse;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.service.ExecutionTraceRecorder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent-platform")
public class AgentPlatformController {

    private final ToolExecutionService toolExecutionService;
    private final TaskOrchestratorService taskOrchestratorService;
    private final ExecutionTraceRecorder traceRecorder;

    public AgentPlatformController(ToolExecutionService toolExecutionService,
                                   TaskOrchestratorService taskOrchestratorService,
                                   ExecutionTraceRecorder traceRecorder) {
        this.toolExecutionService = toolExecutionService;
        this.taskOrchestratorService = taskOrchestratorService;
        this.traceRecorder = traceRecorder;
    }

    @GetMapping("/tools")
    public AgentApiResponse<List<ToolMetadata>> listTools() {
        return AgentApiResponse.success(toolExecutionService.listTools());
    }

    @PostMapping("/tools/{toolName}/execute")
    public AgentApiResponse<ToolExecuteResult> executeByName(@PathVariable String toolName,
                                                             @RequestBody ToolInvokeRequest request) {
        ToolInvokeRequest safeRequest = request == null ? new ToolInvokeRequest() : request;
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("agent-platform.tool:" + toolName, safeRequest, "tool");
        try {
            ToolExecuteResult result = toolExecutionService.executeByName(ToolExecuteRequest.builder()
                    .toolName(toolName)
                    .traceId(safeRequest.getTraceId())
                    .arguments(safeRequest.getArguments())
                    .build());
            traceRecorder.stage(requestId, ExecutionStageType.TOOL, "tool_execute", toolName,
                    safeRequest.getArguments(), result, result.getCostMs(), result.isSuccess(), result.getErrorMessage());
            finishToolTrace(requestId, result, startedAt);
            return AgentApiResponse.success(result);
        } catch (Exception e) {
            traceRecorder.fail(requestId, e, startedAt);
            throw e;
        }
    }

    @PostMapping("/tools/execute-by-tag")
    public AgentApiResponse<ToolExecuteResult> executeByTag(@RequestBody ToolInvokeRequest request) {
        ToolInvokeRequest safeRequest = request == null ? new ToolInvokeRequest() : request;
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("agent-platform.tool-tag:" + safeRequest.getTag(), safeRequest, "tool");
        try {
            ToolExecuteResult result = toolExecutionService.executeByMetadata(safeRequest.getTag(),
                    ToolExecuteRequest.builder()
                            .traceId(safeRequest.getTraceId())
                            .arguments(safeRequest.getArguments())
                            .build());
            traceRecorder.stage(requestId, ExecutionStageType.TOOL, "tool_execute_by_tag", result.getToolName(),
                    safeRequest.getArguments(), result, result.getCostMs(), result.isSuccess(), result.getErrorMessage());
            finishToolTrace(requestId, result, startedAt);
            return AgentApiResponse.success(result);
        } catch (Exception e) {
            traceRecorder.fail(requestId, e, startedAt);
            throw e;
        }
    }

    @PostMapping("/tasks/execute")
    public AgentApiResponse<TaskExecuteResult> executeTask(@RequestBody TaskExecuteRequest request) {
        TaskExecuteRequest safeRequest = request == null ? new TaskExecuteRequest() : request;
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("agent-platform.task", safeRequest, "task-orchestrator");
        try {
            TaskExecuteResult result = taskOrchestratorService.execute(safeRequest);
            logTaskStages(requestId, safeRequest, result);
            finishTaskTrace(requestId, result, startedAt);
            return AgentApiResponse.success(result);
        } catch (Exception e) {
            traceRecorder.fail(requestId, e, startedAt);
            throw e;
        }
    }

    @PostMapping("/tasks/demo")
    public AgentApiResponse<TaskExecuteResult> executeDemoTask(@RequestBody DemoTaskRequest request) {
        DemoTaskRequest safeRequest = request == null ? new DemoTaskRequest() : request;
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("agent-platform.demo-task", safeRequest, "task-orchestrator");
        try {
            TaskExecuteResult result = taskOrchestratorService.runDemoFlow(safeRequest.getQuery() == null ? "" : safeRequest.getQuery());
            logTaskStages(requestId, safeRequest, result);
            finishTaskTrace(requestId, result, startedAt);
            return AgentApiResponse.success(result);
        } catch (Exception e) {
            traceRecorder.fail(requestId, e, startedAt);
            throw e;
        }
    }

    private void logTaskStages(String requestId, Object input, TaskExecuteResult result) {
        if (result.getResults() == null || result.getResults().isEmpty()) {
            traceRecorder.stage(requestId, ExecutionStageType.TOOL, "task_orchestrate", null,
                    input, result, null, result.isSuccess(), null);
            return;
        }
        for (ToolExecuteResult item : result.getResults()) {
            traceRecorder.stage(requestId, ExecutionStageType.TOOL, "task_tool_step", item.getToolName(),
                    input, item, item.getCostMs(), item.isSuccess(), item.getErrorMessage());
        }
    }

    private void finishToolTrace(String requestId, ToolExecuteResult result, long startedAt) {
        if (result.isSuccess()) {
            traceRecorder.success(requestId, result, startedAt);
        } else {
            traceRecorder.fail(requestId, result.getErrorMessage(), startedAt);
        }
    }

    private void finishTaskTrace(String requestId, TaskExecuteResult result, long startedAt) {
        if (result.isSuccess()) {
            traceRecorder.success(requestId, result, startedAt);
        } else {
            traceRecorder.fail(requestId, result.getSummary(), startedAt);
        }
    }
}
