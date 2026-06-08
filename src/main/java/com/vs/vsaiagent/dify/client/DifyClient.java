package com.vs.vsaiagent.dify.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.vsaiagent.dify.config.DifyProperties;
import com.vs.vsaiagent.dify.dto.DifyRunRequest;
import com.vs.vsaiagent.dify.dto.DifyRunResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dify Workflow HTTP 客户端。
 *
 * 走的是 Dify 官方 Workflow API：
 *   POST {base-url}/v1/workflows/run
 * 头部：Authorization: Bearer {app-api-key}
 *
 * 这一层故意保留 raw 响应，便于前端/日志看到 Dify 原始 trace。
 */
@Slf4j
@Component
public class DifyClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DifyProperties props;
    private final RestTemplate restTemplate = new RestTemplate();

    public DifyClient(DifyProperties props) {
        this.props = props;
    }

    /** 配置健康检查 — 给前端 Dify 页用 */
    public boolean isConfigured() { return props.isConfigured(); }

    public DifyProperties getProps() { return props; }

    public DifyRunResult run(DifyRunRequest req) {
        if (!props.isConfigured()) {
            return DifyRunResult.builder()
                    .success(false)
                    .errorMessage("Dify 未配置：请在 application-*.yml 设置 app.dify.base-url / api-key")
                    .build();
        }
        String workflowId = req.getWorkflowId();
        if (workflowId == null || workflowId.isBlank()) workflowId = props.getDefaultWorkflowId();

        String url = trimRight(props.getBaseUrl(), "/") + "/v1/workflows/run";

        Map<String, Object> body = new HashMap<>();
        body.put("inputs", req.getInputs() == null ? Map.of() : req.getInputs());
        body.put("response_mode", req.getResponseMode() == null ? "blocking" : req.getResponseMode());
        body.put("user", req.getUser() == null ? "java-" + UUID.randomUUID() : req.getUser());
        if (workflowId != null && !workflowId.isBlank()) {
            body.put("workflow_id", workflowId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getApiKey());

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            long elapsed = System.currentTimeMillis() - start;
            String raw = resp.getBody();
            JsonNode node = raw == null ? null : MAPPER.readTree(raw);

            String runId = node == null ? null : node.path("workflow_run_id").asText(null);
            String status = node == null ? null : node.path("data").path("status").asText("succeeded");
            Object outputs = node == null ? null : MAPPER.convertValue(node.path("data").path("outputs"), Object.class);

            boolean ok = resp.getStatusCode().is2xxSuccessful()
                    && (status == null || !"failed".equalsIgnoreCase(status));

            return DifyRunResult.builder()
                    .success(ok)
                    .workflowId(workflowId)
                    .workflowRunId(runId)
                    .status(status)
                    .outputs(outputs)
                    .elapsedMs(elapsed)
                    .rawResponse(raw)
                    .errorMessage(ok ? null : "Dify returned non-success status: " + status)
                    .build();
        } catch (Exception e) {
            log.warn("[dify] run failed: {}", e.getMessage());
            return DifyRunResult.builder()
                    .success(false)
                    .workflowId(workflowId)
                    .errorMessage(e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private static String trimRight(String s, String sep) {
        if (s == null) return null;
        while (s.endsWith(sep)) s = s.substring(0, s.length() - sep.length());
        return s;
    }
}
