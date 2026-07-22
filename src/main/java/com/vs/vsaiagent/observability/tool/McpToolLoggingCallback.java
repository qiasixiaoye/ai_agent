package com.vs.vsaiagent.observability.tool;

import com.vs.vsaiagent.observability.context.TraceContext;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.function.Supplier;

/**
 * MCP Server 出口处的工具调用观测装饰器。
 *
 * <p>区别于 {@link LoggingToolCallback}（内部 agent 链路用，依赖已存在的 TraceContext.requestId）：
 * Dify 等外部平台经 MCP 打过来的工具调用，进来时并没有一条已落库的 observability request
 * （{@code TraceContextFilter} 只在 ThreadLocal 里塞了个随机、未落库的 requestId）。本装饰器为
 * <b>每一次 MCP 工具调用自起一条 {@code mcp-tool-call} request</b>，把真实入参 / 出参 / 耗时 / 报错
 * 落进审计模块，填补「Dify Agent 调 MCP 工具在后端完全不可观测」的盲区（设计见
 * {@code docs/mcp-tool-trace-correlation.md} 的 L1）。</p>
 *
 * <p>request 的 sessionId 取 MCP 传输层会话 id（SSE 握手分配、POST {@code /mcp/message?sessionId=...}
 * 携带），作为后续运行时关联（L2 的 A+C 策略）的二级过滤键。</p>
 *
 * <p>观测写入全程吞异常：任何记录失败都不得影响工具本身的执行与返回。</p>
 */
public class McpToolLoggingCallback implements ToolCallback {

    private static final String SCENE = "mcp-tool-call";
    private static final String MODEL = "dify-mcp";

    private final ToolCallback delegate;
    private final ExecutionLogService executionLogService;

    public McpToolLoggingCallback(ToolCallback delegate, ExecutionLogService executionLogService) {
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
        String mcpSessionId = currentMcpSessionId();
        // 让本次请求内后续 stage 也带上 MCP sessionId（startRequest/finishX 内部记的 stage 会读 TraceContext）
        if (mcpSessionId != null) {
            safe(() -> TraceContext.setSessionId(mcpSessionId));
        }
        String requestId = startRequest(mcpSessionId, toolInput);

        try {
            String output = invoke.get();
            stage(requestId, toolInput, output, System.currentTimeMillis() - start, true, null);
            finishSuccess(requestId, output, start);
            return output;
        } catch (RuntimeException e) {
            stage(requestId, toolInput, null, System.currentTimeMillis() - start, false, e.getMessage());
            finishFail(requestId, e.getMessage(), start);
            throw e;
        }
    }

    private String startRequest(String mcpSessionId, String toolInput) {
        try {
            return executionLogService.startRequest(SCENE, mcpSessionId,
                    "tool=" + delegate.getName() + " args=" + toolInput, MODEL);
        } catch (Exception ignore) {
            return null;
        }
    }

    private void stage(String requestId, String input, String output, long costMs, boolean success, String error) {
        if (requestId == null) {
            return;
        }
        safe(() -> executionLogService.logStage(requestId, ExecutionStageType.TOOL, "tool_call",
                delegate.getName(), input, output, costMs, success, error));
    }

    private void finishSuccess(String requestId, String output, long start) {
        if (requestId == null) {
            return;
        }
        safe(() -> executionLogService.finishSuccess(requestId, output, System.currentTimeMillis() - start));
    }

    private void finishFail(String requestId, String error, long start) {
        if (requestId == null) {
            return;
        }
        safe(() -> executionLogService.finishFail(requestId, error, System.currentTimeMillis() - start));
    }

    /** 从当前 HTTP 请求读 MCP 传输层 sessionId（{@code /mcp/message?sessionId=...}）；拿不到返回 null。 */
    private String currentMcpSessionId() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                String sid = attrs.getRequest().getParameter("sessionId");
                return (sid == null || sid.isBlank()) ? null : sid;
            }
        } catch (Exception ignore) {
            // 非 web 上下文或读取失败：忽略，sessionId 退化为 default
        }
        return null;
    }

    private void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception ignore) {
            // 观测写入失败不得影响工具执行
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
