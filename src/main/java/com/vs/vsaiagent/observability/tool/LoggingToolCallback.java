package com.vs.vsaiagent.observability.tool;

import com.vs.vsaiagent.observability.context.TraceContext;
import com.vs.vsaiagent.observability.context.TraceInfo;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.function.Supplier;

public class LoggingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ExecutionLogService executionLogService;

    public LoggingToolCallback(ToolCallback delegate, ExecutionLogService executionLogService) {
        this.delegate = delegate;
        this.executionLogService = executionLogService;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return doCall(toolInput, () -> delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return doCall(toolInput, () -> delegate.call(toolInput, toolContext));
    }

    private String doCall(String toolInput, Supplier<String> invoke) {
        long start = System.currentTimeMillis();
        TraceInfo traceInfo = TraceContext.get();
        String requestId = traceInfo == null ? null : traceInfo.requestId();
        try {
            String output = invoke.get();
            if (requestId != null) {
                executionLogService.logStage(
                        requestId,
                        ExecutionStageType.TOOL,
                        "tool_call",
                        delegate.getName(),
                        toolInput,
                        output,
                        System.currentTimeMillis() - start,
                        true,
                        null
                );
            }
            return output;
        } catch (Exception e) {
            if (requestId != null) {
                executionLogService.logStage(
                        requestId,
                        ExecutionStageType.TOOL,
                        "tool_call",
                        delegate.getName(),
                        toolInput,
                        null,
                        System.currentTimeMillis() - start,
                        false,
                        e.getMessage()
                );
            }
            throw e;
        }
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public String getInputTypeSchema() {
        return delegate.getInputTypeSchema();
    }
}
