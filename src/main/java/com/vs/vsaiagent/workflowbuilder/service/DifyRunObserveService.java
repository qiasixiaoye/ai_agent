package com.vs.vsaiagent.workflowbuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.vsaiagent.dify.client.DifyConsoleClient;
import com.vs.vsaiagent.dify.dto.DifyDraftRunResult;
import com.vs.vsaiagent.observability.entity.AgentRequestLogEntity;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.repository.AgentRequestLogRepository;
import com.vs.vsaiagent.observability.service.ExecutionTraceRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在 Dify 里草稿运行一个已导入应用，并把运行时事件（节点结果、Agent 轮次、整体状态）
 * 解析成结构化轨迹 + 落入 observability 模块，让 Agent / 工作流的**运行时错误对后端可观测**。
 *
 * 这填补了之前的盲区：以前只能看到「生成 / 导入」阶段，运行时（工具报错、Agent 没收敛、填错参数）
 * 全在 Dify 里看不到；现在每个 node_finished / 失败的 agent_log 都会记成一个 stage，可在错误分析审计里回查。
 */
@Slf4j
@Service
public class DifyRunObserveService {

    /** mcp-tool-call 审计场景名，与 McpToolLoggingCallback 一致。 */
    private static final String MCP_SCENE = "mcp-tool-call";
    /** 时间窗两端各放宽的余量（毫秒），抵消时钟/落库顺序的微小偏差。 */
    private static final long WINDOW_SLACK_MS = 2000L;

    private final DifyConsoleClient difyConsoleClient;
    private final ExecutionTraceRecorder recorder;
    private final AgentRequestLogRepository requestLogRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public DifyRunObserveService(DifyConsoleClient difyConsoleClient,
                                 ExecutionTraceRecorder recorder,
                                 AgentRequestLogRepository requestLogRepository) {
        this.difyConsoleClient = difyConsoleClient;
        this.recorder = recorder;
        this.requestLogRepository = requestLogRepository;
    }

    public DifyDraftRunResult run(String appId, boolean advancedChat, String query, Map<String, Object> inputs) {
        long t0 = System.currentTimeMillis();
        Map<String, Object> startInput = new LinkedHashMap<>();
        startInput.put("appId", appId);
        startInput.put("query", query == null ? "" : query);
        String requestId = recorder.start("workflow-builder-dify-run", startInput, "dify-runtime");

        try {
            String sse = difyConsoleClient.draftRunRaw(appId, advancedChat, query, inputs);
            return parseAndRecord(sse, requestId, t0);
        } catch (Exception e) {
            log.warn("[dify-run] 运行失败 appId={}", appId, e);
            recorder.stage(requestId, ExecutionStageType.ERROR, "运行调用失败", "dify-console",
                    null, null, null, false, e.getMessage());
            recorder.fail(requestId, e.getMessage(), t0);
            return new DifyDraftRunResult(false, "failed", e.getMessage(),
                    null, null, null, List.of(), List.of(), requestId, List.of());
        }
    }

    private DifyDraftRunResult parseAndRecord(String sse, String requestId, long t0) {
        List<DifyDraftRunResult.NodeStep> nodes = new ArrayList<>();
        List<DifyDraftRunResult.AgentRound> rounds = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        String wfStatus = null;
        String wfError = null;
        Double elapsed = null;
        Integer tokens = null;

        for (String line : sse.split("\n")) {
            line = line.trim();
            if (!line.startsWith("data:")) {
                continue;
            }
            JsonNode root;
            try {
                root = mapper.readTree(line.substring("data:".length()).trim());
            } catch (Exception ignore) {
                continue;
            }
            String event = root.path("event").asText("");
            JsonNode data = root.path("data");

            switch (event) {
                case "node_finished" -> {
                    String nodeType = data.path("node_type").asText(null);
                    String title = data.path("title").asText(null);
                    String status = data.path("status").asText(null);
                    String error = textOrNull(data, "error");
                    Double et = data.has("elapsed_time") ? data.path("elapsed_time").asDouble() : null;
                    DifyDraftRunResult.NodeStep step = new DifyDraftRunResult.NodeStep(nodeType, title, status, error, et);
                    nodes.add(step);
                    recorder.stage(requestId, stageType(nodeType), title, nodeType,
                            null, null, et == null ? null : (long) (et * 1000), step.ok(), error);
                }
                case "agent_log" -> {
                    String label = data.path("label").asText(null);
                    String nodeId = data.path("node_id").asText(null);
                    String status = data.path("status").asText(null);
                    String error = textOrNull(data, "error");
                    DifyDraftRunResult.AgentRound round = new DifyDraftRunResult.AgentRound(label, nodeId, status, error);
                    rounds.add(round);
                    // 只有失败的轮次才单独记一条 ERROR stage，避免淹没（成功轮次保留在 agentRounds 里）
                    if (round.failed()) {
                        recorder.stage(requestId, ExecutionStageType.ERROR, label, "agent:" + nodeId,
                                null, null, null, false, error);
                    }
                }
                case "message" -> {
                    String token = textOrNull(root, "answer");
                    if (token == null) {
                        token = textOrNull(data, "answer");
                    }
                    if (token != null) {
                        answer.append(token);
                    }
                }
                case "workflow_finished" -> {
                    wfStatus = data.path("status").asText(null);
                    wfError = textOrNull(data, "error");
                    elapsed = data.has("elapsed_time") ? data.path("elapsed_time").asDouble() : null;
                    tokens = data.has("total_tokens") ? data.path("total_tokens").asInt() : null;
                }
                default -> {
                    // workflow_started / node_started / message_end 等无需记录
                }
            }
        }

        // 运行时关联（A+C）：把本次运行窗口 [t0, now] 内的 mcp-tool-call 审计行，按工具名跟 ROUND 对齐，
        // 关联到本次运行；写一条关联 stage，并随结果返回，供前端深查。这是启发式关联，非精确因果对账。
        List<DifyDraftRunResult.CorrelatedToolCall> correlated =
                correlateMcpToolCalls(requestId, t0, System.currentTimeMillis(), rounds);

        boolean success = "succeeded".equalsIgnoreCase(wfStatus);
        if (success) {
            recorder.success(requestId, answer.toString(), t0);
        } else {
            recorder.fail(requestId, wfError != null ? wfError : ("运行未成功，状态=" + wfStatus), t0);
        }
        return new DifyDraftRunResult(success, wfStatus, wfError, elapsed, tokens,
                answer.toString(), nodes, rounds, requestId, correlated);
    }

    /**
     * 启发式关联（时间窗 + 工具名）：查 [t0,t1]（含余量）内 scene=mcp-tool-call 的审计行，
     * 若本次运行从 SSE 解析出 CALL &lt;tool&gt; 列表，则只保留工具名命中的，收窄并发误配；否则全部纳入。
     * 关联结果写一条 OUTPUT stage 到本次运行的 requestId 下，并返回给调用方。
     */
    private List<DifyDraftRunResult.CorrelatedToolCall> correlateMcpToolCalls(
            String runRequestId, long t0, long t1, List<DifyDraftRunResult.AgentRound> rounds) {
        try {
            Set<String> calledTools = rounds.stream()
                    .map(r -> extractCalledTool(r.label()))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            List<AgentRequestLogEntity> rows = requestLogRepository.listBySceneBetween(
                    MCP_SCENE, toLdt(t0 - WINDOW_SLACK_MS), toLdt(t1 + WINDOW_SLACK_MS), 200);

            List<DifyDraftRunResult.CorrelatedToolCall> out = new ArrayList<>();
            for (AgentRequestLogEntity row : rows) {
                String tool = extractToolName(row.getUserInput());
                if (!calledTools.isEmpty() && tool != null && !calledTools.contains(tool)) {
                    continue;   // 工具名对齐过滤：本次没调过的同窗口工具，不纳入
                }
                boolean ok = "SUCCESS".equalsIgnoreCase(row.getStatus());
                out.add(new DifyDraftRunResult.CorrelatedToolCall(
                        row.getRequestId(), tool, ok, row.getTotalCostMs()));
            }

            if (!out.isEmpty()) {
                String summary = "启发式关联(时间窗+工具名) " + out.size() + " 次 MCP 工具调用：" + summarize(out);
                recorder.stage(runRequestId, ExecutionStageType.OUTPUT, "关联MCP工具调用", null,
                        null, summary, null, true, null);
            }
            return out;
        } catch (Exception e) {
            log.warn("[dify-run] 关联 MCP 工具调用失败 runRequestId={}", runRequestId, e);
            return List.of();
        }
    }

    /** 从 agent_request_log.user_input（形如 {@code tool=<name> args={...}}）里抽工具名。 */
    static String extractToolName(String userInput) {
        if (userInput == null || !userInput.startsWith("tool=")) {
            return null;
        }
        int argsIdx = userInput.indexOf(" args=");
        String name = (argsIdx > 0 ? userInput.substring(5, argsIdx) : userInput.substring(5)).trim();
        return name.isEmpty() ? null : name;
    }

    /** 从 agent_log 的 label（形如 {@code CALL <tool>}）里抽被调用的工具名；非 CALL 行返回 null。 */
    static String extractCalledTool(String label) {
        if (label == null) {
            return null;
        }
        String t = label.trim();
        if (!t.regionMatches(true, 0, "CALL ", 0, 5)) {
            return null;
        }
        String name = t.substring(5).trim();
        return name.isEmpty() ? null : name;
    }

    private static String summarize(List<DifyDraftRunResult.CorrelatedToolCall> calls) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DifyDraftRunResult.CorrelatedToolCall c : calls) {
            counts.merge(c.toolName() == null ? "?" : c.toolName(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(e -> e.getKey() + "×" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static LocalDateTime toLdt(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private ExecutionStageType stageType(String nodeType) {
        if (nodeType == null) {
            return ExecutionStageType.MODEL;
        }
        return switch (nodeType) {
            case "start" -> ExecutionStageType.INPUT;
            case "tool", "http-request" -> ExecutionStageType.TOOL;
            case "knowledge-retrieval" -> ExecutionStageType.RETRIEVAL;
            case "answer", "end", "template-transform" -> ExecutionStageType.OUTPUT;
            default -> ExecutionStageType.MODEL;   // llm / agent / 其它
        };
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText(null);
        return (s == null || s.isBlank()) ? null : s;
    }
}
