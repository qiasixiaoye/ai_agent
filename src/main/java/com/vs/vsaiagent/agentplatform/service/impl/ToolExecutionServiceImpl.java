package com.vs.vsaiagent.agentplatform.service.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.registry.ToolRegistry;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import com.vs.vsaiagent.agentplatform.tool.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class ToolExecutionServiceImpl implements ToolExecutionService {

    private final ToolRegistry toolRegistry;

    public ToolExecutionServiceImpl(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public ToolExecuteResult executeByName(ToolExecuteRequest request) {
        String traceId = request.getTraceId() == null ? UUID.randomUUID().toString() : request.getTraceId();
        AgentTool tool = toolRegistry.findByName(request.getToolName())
                .orElseThrow(() -> new IllegalArgumentException("未找到工具: " + request.getToolName()));
        ToolExecuteRequest enriched = ToolExecuteRequest.builder()
                .traceId(traceId)
                .toolName(tool.toolName())
                .arguments(request.getArguments())
                .build();
        tool.validate(enriched);
        return executeWithTimeout(tool, enriched);
    }

    @Override
    public ToolExecuteResult executeByMetadata(String tag, ToolExecuteRequest request) {
        AgentTool tool = toolRegistry.searchByTag(tag).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到匹配 tag 的工具: " + tag));
        ToolExecuteRequest enriched = ToolExecuteRequest.builder()
                .traceId(request.getTraceId())
                .toolName(tool.toolName())
                .arguments(request.getArguments())
                .build();
        tool.validate(enriched);
        return executeWithTimeout(tool, enriched);
    }

    @Override
    public List<ToolMetadata> listTools() {
        return toolRegistry.listMetadata();
    }

    private ToolExecuteResult executeWithTimeout(AgentTool tool, ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        long timeoutMs = tool.metadata().getTimeoutMs() == null ? 10000L : tool.metadata().getTimeoutMs();
        try {
            CompletableFuture<ToolExecuteResult> future = CompletableFuture.supplyAsync(() -> tool.execute(request));
            ToolExecuteResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.info("traceId={}, tool={}, success={}, cost={}ms", request.getTraceId(), tool.toolName(), result.isSuccess(), result.getCostMs());
            return result;
        } catch (TimeoutException e) {
            return ToolExecuteResult.builder()
                    .toolName(tool.toolName())
                    .success(false)
                    .errorMessage("工具执行超时")
                    .costMs(System.currentTimeMillis() - start)
                    .build();
        } catch (ExecutionException e) {
            String message = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            return ToolExecuteResult.builder()
                    .toolName(tool.toolName())
                    .success(false)
                    .errorMessage(message)
                    .costMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return ToolExecuteResult.builder()
                    .toolName(tool.toolName())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .costMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
