package com.vs.vsaiagent.dify.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vs.vsaiagent.dify.config.DifyConsoleProperties;
import com.vs.vsaiagent.dify.dto.DifyImportResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Dify Console API 客户端：登录拿 access_token + 导入 DSL 创建应用。
 *
 * 端点（Dify 1.x）：
 *   POST {base}/console/api/login                  → data.access_token
 *   POST {base}/console/api/apps/imports           → {id, status, app_id, error}
 * 旧版（0.x 早期）回退：
 *   POST {base}/console/api/apps/import            → {id, ...}
 *
 * token 进程内缓存，401 时清空并重登一次。
 */
@Slf4j
@Component
public class DifyConsoleClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DifyConsoleProperties props;
    private final RestTemplate restTemplate = new RestTemplate();

    private volatile String cachedToken;

    public DifyConsoleClient(DifyConsoleProperties props) {
        this.props = props;
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public DifyImportResult importDsl(String yamlContent) {
        if (!props.isConfigured()) {
            return DifyImportResult.builder()
                    .success(false)
                    .errorMessage("Dify Console 未配置：请设置 app.dify.console.base-url + email/password（或 access-token）")
                    .build();
        }
        try {
            return doImport(yamlContent, false);
        } catch (Exception e) {
            log.warn("[dify-console] import failed", e);
            return DifyImportResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    private DifyImportResult doImport(String yamlContent, boolean retried) {
        String token = currentToken();

        ObjectNode body = MAPPER.createObjectNode();
        body.put("mode", "yaml-content");
        body.put("yaml_content", yamlContent);

        ResponseEntity<String> resp;
        try {
            resp = post(url("/console/api/apps/imports"), token, body.toString());
        } catch (HttpClientErrorException.NotFound e) {
            // 旧版 Dify 回退
            ObjectNode legacy = MAPPER.createObjectNode();
            legacy.put("data", yamlContent);
            resp = post(url("/console/api/apps/import"), token, legacy.toString());
        } catch (HttpClientErrorException.Unauthorized e) {
            if (retried) {
                throw e;
            }
            cachedToken = null;
            return doImport(yamlContent, true);
        }

        return parseImportResponse(resp);
    }

    private DifyImportResult parseImportResponse(ResponseEntity<String> resp) {
        String raw = resp.getBody();
        JsonNode node;
        try {
            node = raw == null ? MAPPER.createObjectNode() : MAPPER.readTree(raw);
        } catch (Exception e) {
            return DifyImportResult.builder()
                    .success(resp.getStatusCode().is2xxSuccessful())
                    .rawResponse(raw)
                    .errorMessage("响应不是 JSON: " + e.getMessage())
                    .build();
        }

        // 1.x: {id, status, app_id, error}; 旧版: 直接是 app 对象 {id, name, ...}
        String status = node.path("status").asText(null);
        String appId = node.path("app_id").asText(null);
        if (appId == null || appId.isBlank()) {
            appId = node.path("id").asText(null);
        }
        String error = node.path("error").asText(null);

        boolean ok = resp.getStatusCode().is2xxSuccessful()
                && (status == null || status.startsWith("completed"))
                && (error == null || error.isBlank());

        return DifyImportResult.builder()
                .success(ok)
                .appId(appId)
                .status(status)
                .appUrl(ok && appId != null ? url("/app/" + appId + "/workflow") : null)
                .errorMessage(ok ? null : (error != null && !error.isBlank() ? error : "Dify 导入状态: " + status))
                .rawResponse(raw)
                .build();
    }

    private String currentToken() {
        if (props.getAccessToken() != null && !props.getAccessToken().isBlank()) {
            return props.getAccessToken();
        }
        String token = cachedToken;
        if (token != null) {
            return token;
        }
        synchronized (this) {
            if (cachedToken == null) {
                cachedToken = login();
            }
            return cachedToken;
        }
    }

    private String login() {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", props.getEmail());
        body.put("password", props.getPassword());
        body.put("language", "zh-Hans");
        body.put("remember_me", true);

        ResponseEntity<String> resp = post(url("/console/api/login"), null, body.toString());
        try {
            JsonNode node = MAPPER.readTree(resp.getBody() == null ? "{}" : resp.getBody());
            // 1.x: {result: success, data: {access_token, refresh_token}}；部分旧版: {data: "<token>"}
            JsonNode data = node.path("data");
            String token = data.isTextual() ? data.asText() : data.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("登录成功但未取到 access_token，原始响应: " + truncate(resp.getBody()));
            }
            log.info("[dify-console] login ok");
            return token;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析 Dify 登录响应失败: " + e.getMessage(), e);
        }
    }

    private ResponseEntity<String> post(String url, String bearerToken, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        ResponseEntity<String> resp = restTemplate.postForEntity(url, new HttpEntity<>(jsonBody, headers), String.class);
        HttpStatusCode code = resp.getStatusCode();
        if (!code.is2xxSuccessful()) {
            throw new IllegalStateException("Dify Console 返回 " + code.value() + ": " + truncate(resp.getBody()));
        }
        return resp;
    }

    private String url(String path) {
        String base = props.getBaseUrl();
        while (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
