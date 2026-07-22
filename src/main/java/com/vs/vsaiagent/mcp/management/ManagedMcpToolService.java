package com.vs.vsaiagent.mcp.management;

import com.vs.vsaiagent.context.ToolResultCompressor;
import com.vs.vsaiagent.context.ContextBudgetManager;
import com.vs.vsaiagent.context.TokenEstimator;
import com.vs.vsaiagent.memory.HierarchicalChatMemory;
import com.vs.vsaiagent.observability.context.TraceContext;
import com.vs.vsaiagent.observability.context.TraceInfo;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MCP Client 工具治理层：目录缓存、策略过滤、显式调用、超时、熔断、审计和情景记忆。
 */
@Service
public class ManagedMcpToolService implements DisposableBean {

    private final McpToolPolicy policy;
    private final ExecutionLogService executionLogService;
    private final HierarchicalChatMemory memory;
    private final long catalogTtlMs;
    private final long timeoutMs;
    private final int failureThreshold;
    private final long circuitResetMs;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    @Autowired(required = false)
    @Qualifier("toolCallbacks")
    private ToolCallbackProvider provider;

    @Autowired(required = false)
    private ToolResultCompressor toolResultCompressor;

    @Autowired(required = false)
    private ContextBudgetManager contextBudgetManager;

    @Autowired(required = false)
    private TokenEstimator tokenEstimator;

    private volatile Catalog catalog = new Catalog(Map.of(), 0);

    public ManagedMcpToolService(McpToolPolicy policy,
                                 ExecutionLogService executionLogService,
                                 HierarchicalChatMemory memory,
                                 @Value("${app.mcp.catalog-ttl-ms:30000}") long catalogTtlMs,
                                 @Value("${app.mcp.tool-timeout-ms:15000}") long timeoutMs,
                                 @Value("${app.mcp.circuit.failure-threshold:3}") int failureThreshold,
                                 @Value("${app.mcp.circuit.reset-ms:60000}") long circuitResetMs) {
        this.policy = policy;
        this.executionLogService = executionLogService;
        this.memory = memory;
        this.catalogTtlMs = Math.max(1000, catalogTtlMs);
        this.timeoutMs = Math.max(100, timeoutMs);
        this.failureThreshold = Math.max(1, failureThreshold);
        this.circuitResetMs = Math.max(1000, circuitResetMs);
    }

    public synchronized List<McpToolDescriptor> refreshCatalog() {
        Map<String, ToolCallback> tools = new LinkedHashMap<>();
        if (provider != null && provider.getToolCallbacks() != null) {
            Arrays.stream(provider.getToolCallbacks())
                    .filter(ToolCallback.class::isInstance)
                    .map(ToolCallback.class::cast)
                    .forEach(callback -> tools.put(callback.getName(), callback));
        }
        catalog = new Catalog(Map.copyOf(tools), System.currentTimeMillis());
        return descriptors(catalog);
    }

    public List<McpToolDescriptor> listTools() {
        return descriptors(currentCatalog());
    }

    public McpRuntimeHealth health() {
        Catalog current = currentCatalog();
        List<McpToolDescriptor> descriptors = descriptors(current);
        return new McpRuntimeHealth(provider != null, descriptors.size(),
                (int) descriptors.stream().filter(McpToolDescriptor::enabled).count(),
                (int) descriptors.stream().filter(d -> "OPEN".equals(d.circuitState())).count(),
                current.refreshedAt());
    }

    /** 给 LLM 的工具集默认排除需要人工确认的高风险工具。 */
    public ToolCallback[] allowedToolCallbacks() {
        return allowedToolCallbacks("");
    }

    /** Query-aware MCP schema loading: expose only the most relevant tools that fit the schema budget. */
    public ToolCallback[] allowedToolCallbacks(String query) {
        int schemaBudget = contextBudgetManager == null
                ? 2000 : contextBudgetManager.cap(ContextBudgetManager.TOOL_SCHEMAS);
        List<ToolCallback> candidates = currentCatalog().tools().values().stream()
                .filter(callback -> policy.evaluate(callback.getName(), false).allowed())
                .filter(callback -> !isOpen(callback.getName()))
                .sorted(Comparator.comparingDouble((ToolCallback callback) -> toolRelevance(query, callback)).reversed()
                        .thenComparing(ToolCallback::getName))
                .toList();
        List<ToolCallback> selected = new ArrayList<>();
        int used = 0;
        for (ToolCallback callback : candidates) {
            if (selected.size() >= 8) break;
            int tokens = estimateSchemaTokens(callback);
            if (!selected.isEmpty() && used + tokens > schemaBudget) continue;
            selected.add(new ManagedCallback(callback));
            used += tokens;
        }
        return selected.toArray(ToolCallback[]::new);
    }

    public McpToolCallResult invoke(String toolName, String argumentsJson, boolean confirmed) {
        ToolCallback callback = currentCatalog().tools().get(toolName);
        if (callback == null) return failure(toolName, "MCP tool not found", 0);
        McpPolicyDecision decision = policy.evaluate(toolName, confirmed);
        if (!decision.allowed()) return failure(toolName, decision.reason(), 0);
        return execute(callback, argumentsJson == null ? "{}" : argumentsJson);
    }

    private McpToolCallResult execute(ToolCallback callback, String argumentsJson) {
        long start = System.currentTimeMillis();
        String toolName = callback.getName();
        if (isOpen(toolName)) return failure(toolName, "circuit is open", 0);
        TraceInfo trace = TraceContext.get();
        String requestId = trace == null ? null : trace.requestId();
        Future<String> future = executor.submit(() -> callback.call(argumentsJson));
        try {
            String output = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            circuits.remove(toolName);
            long cost = System.currentTimeMillis() - start;
            audit(requestId, toolName, argumentsJson, output, cost, true, null);
            rememberEpisode(trace, toolName, output, true);
            return new McpToolCallResult(toolName, true, output, null, cost, "CLOSED");
        } catch (TimeoutException e) {
            future.cancel(true);
            return onFailure(requestId, trace, toolName, argumentsJson, "tool execution timeout", start);
        } catch (Exception e) {
            String message = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            return onFailure(requestId, trace, toolName, argumentsJson, message, start);
        }
    }

    private McpToolCallResult onFailure(String requestId, TraceInfo trace, String toolName,
                                        String input, String error, long start) {
        CircuitState state = circuits.compute(toolName, (name, old) -> {
            int failures = old == null ? 1 : old.failures() + 1;
            long openUntil = failures >= failureThreshold ? System.currentTimeMillis() + circuitResetMs : 0;
            return new CircuitState(failures, openUntil);
        });
        long cost = System.currentTimeMillis() - start;
        audit(requestId, toolName, input, null, cost, false, error);
        rememberEpisode(trace, toolName, error, false);
        return new McpToolCallResult(toolName, false, null, error, cost,
                state.openUntil() > System.currentTimeMillis() ? "OPEN" : "CLOSED");
    }

    private void audit(String requestId, String toolName, String input, String output,
                       long cost, boolean success, String error) {
        if (requestId == null) return;
        try {
            executionLogService.logStage(requestId, ExecutionStageType.TOOL, "managed_mcp_tool_call", toolName,
                    redact(input), redact(output), cost, success, redact(error));
        } catch (Exception ignore) {
            // 观测失败不能影响工具结果
        }
    }

    private void rememberEpisode(TraceInfo trace, String toolName, String result, boolean success) {
        if (trace == null || trace.sessionId() == null || trace.sessionId().isBlank()) return;
        try {
            memory.rememberEpisode(trace.sessionId(),
                    "MCP工具 " + toolName + " " + (success ? "执行成功" : "执行失败") + ": " + truncate(result, 800),
                    success ? 0.65 : 0.8, Map.of("tool", toolName, "success", String.valueOf(success)));
        } catch (Exception ignore) {
        }
    }

    private List<McpToolDescriptor> descriptors(Catalog source) {
        List<McpToolDescriptor> result = new ArrayList<>();
        for (ToolCallback callback : source.tools().values()) {
            McpPolicyDecision decision = policy.evaluate(callback.getName(), false);
            CircuitState state = circuits.getOrDefault(callback.getName(), new CircuitState(0, 0));
            boolean open = state.openUntil() > System.currentTimeMillis();
            result.add(new McpToolDescriptor(callback.getName(), callback.getDescription(), callback.getInputTypeSchema(),
                    decision.allowed() && !open, decision.riskLevel(), decision.confirmationRequired(),
                    open ? "OPEN" : "CLOSED", state.failures(), state.openUntil()));
        }
        return result.stream().sorted(Comparator.comparing(McpToolDescriptor::name)).toList();
    }

    private Catalog currentCatalog() {
        Catalog current = catalog;
        if (System.currentTimeMillis() - current.refreshedAt() > catalogTtlMs) return new CatalogHolder().refresh();
        return current;
    }

    private boolean isOpen(String toolName) {
        CircuitState state = circuits.get(toolName);
        if (state == null) return false;
        if (state.openUntil() == 0) return false;
        if (state.openUntil() <= System.currentTimeMillis()) {
            circuits.remove(toolName);
            return false;
        }
        return true;
    }

    private McpToolCallResult failure(String toolName, String error, long cost) {
        return new McpToolCallResult(toolName, false, null, error, cost, isOpen(toolName) ? "OPEN" : "CLOSED");
    }

    private static String redact(String value) {
        if (value == null) return null;
        return value.replaceAll("(?i)(\\\"?(?:api[_-]?key|token|password|secret)\\\"?\\s*[:=]\\s*\\\")[^\\\"]+", "$1***");
    }

    private int estimateSchemaTokens(ToolCallback callback) {
        String schema = callback.getName() + "\n" + callback.getDescription() + "\n" + callback.getInputTypeSchema();
        return tokenEstimator == null ? Math.max(1, schema.length() / 3) : tokenEstimator.estimate(schema);
    }

    private double toolRelevance(String query, ToolCallback callback) {
        String q = normalize(query);
        if (q.isBlank()) return 0;
        String text = normalize(callback.getName() + " " + callback.getDescription());
        if (text.contains(q) || q.contains(text)) return 1;
        int hits = 0;
        for (int i = 0; i < q.length() - 1; i++) {
            if (text.contains(q.substring(i, i + 2))) hits++;
        }
        return q.length() < 2 ? 0 : hits / (double) (q.length() - 1);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    private final class CatalogHolder {
        Catalog refresh() {
            refreshCatalog();
            return catalog;
        }
    }

    private final class ManagedCallback implements ToolCallback {
        private final ToolCallback delegate;

        private ManagedCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
        @Override public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
        @Override public String call(String input) { return resultOrThrow(execute(delegate, input)); }
        @Override public String call(String input, ToolContext context) { return resultOrThrow(execute(delegate, input)); }
        @Override public String getName() { return delegate.getName(); }
        @Override public String getDescription() { return delegate.getDescription(); }
        @Override public String getInputTypeSchema() { return delegate.getInputTypeSchema(); }

        private String resultOrThrow(McpToolCallResult result) {
            if (!result.success()) throw new IllegalStateException(result.errorMessage());
            return toolResultCompressor == null
                    ? result.output()
                    : toolResultCompressor.compress(result.output()).content();
        }
    }

    private record Catalog(Map<String, ToolCallback> tools, long refreshedAt) { }
    private record CircuitState(int failures, long openUntil) { }
}
