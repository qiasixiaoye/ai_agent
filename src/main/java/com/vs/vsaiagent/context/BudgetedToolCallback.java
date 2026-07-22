package com.vs.vsaiagent.context;

import com.vs.vsaiagent.observability.context.TraceContext;
import com.vs.vsaiagent.observability.context.TraceInfo;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** Compresses only the value returned to the model; the inner logging callback sees the original result. */
public class BudgetedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolResultCompressor compressor;
    private final ExecutionLogService logService;

    public BudgetedToolCallback(ToolCallback delegate, ToolResultCompressor compressor,
                                ExecutionLogService logService) {
        this.delegate = delegate;
        this.compressor = compressor;
        this.logService = logService;
    }

    @Override public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
    @Override public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
    @Override public String getName() { return delegate.getName(); }
    @Override public String getDescription() { return delegate.getDescription(); }
    @Override public String getInputTypeSchema() { return delegate.getInputTypeSchema(); }

    @Override
    public String call(String toolInput) {
        return compress(delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return compress(delegate.call(toolInput, toolContext));
    }

    private String compress(String output) {
        CompressionResult result = compressor.compress(output);
        if (result.compressed()) {
            TraceInfo trace = TraceContext.get();
            if (trace != null && trace.requestId() != null) {
                logService.logStage(trace.requestId(), ExecutionStageType.TOOL,
                        "tool_result_compress", delegate.getName(),
                        "originalTokens=" + result.originalTokens(),
                        "retainedTokens=" + result.retainedTokens() + ",strategy=" + result.strategy(),
                        0L, true, null);
            }
        }
        return result.content();
    }
}
