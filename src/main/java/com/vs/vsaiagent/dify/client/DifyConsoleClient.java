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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    /** 草稿运行返回 SSE 流（可能长达数十秒），单独用带超时的模板，避免拖死默认模板。 */
    private final RestTemplate streamingRestTemplate = buildStreamingTemplate();

    private volatile Auth cachedAuth;

    private static RestTemplate buildStreamingTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(180_000);
        return new RestTemplate(factory);
    }

    /** 登录后的鉴权材料：Dify 对 POST 有 CSRF 保护，需同时带 Bearer + Cookie + X-CSRF-Token。 */
    private record Auth(String token, String csrf, String cookieHeader) {
    }

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
        Auth auth = currentAuth();

        ObjectNode body = MAPPER.createObjectNode();
        body.put("mode", "yaml-content");
        body.put("yaml_content", yamlContent);

        ResponseEntity<String> resp;
        try {
            resp = post(url("/console/api/apps/imports"), auth, body.toString());
        } catch (HttpClientErrorException.NotFound e) {
            // 旧版 Dify 回退
            ObjectNode legacy = MAPPER.createObjectNode();
            legacy.put("data", yamlContent);
            resp = post(url("/console/api/apps/import"), auth, legacy.toString());
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            // 401（token 过期）或 403（CSRF）→ 清缓存重登一次
            if (retried) {
                throw e;
            }
            cachedAuth = null;
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

    /**
     * 草稿运行一个已导入的 Dify 应用，返回原始 SSE 事件流文本（由上层解析 node_finished / agent_log /
     * workflow_finished 等事件，用于运行时观测与错误暴露）。
     *
     * @param advancedChat true → advanced-chat(Chatflow) 端点（用 query 对话）；false → workflow 端点（用 inputs）
     */
    public String draftRunRaw(String appId, boolean advancedChat, String query, Map<String, Object> inputs) {
        if (!props.isConfigured()) {
            throw new IllegalStateException("Dify Console 未配置，无法运行");
        }
        return doDraftRun(appId, advancedChat, query, inputs, false);
    }

    private String doDraftRun(String appId, boolean advancedChat, String query, Map<String, Object> inputs, boolean retried) {
        Auth auth = currentAuth();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputs", inputs == null ? Map.of() : inputs);
        payload.put("response_mode", "streaming");
        String path;
        if (advancedChat) {
            payload.put("query", query == null ? "" : query);
            payload.put("conversation_id", "");
            payload.put("files", List.of());
            path = "/console/api/apps/" + appId + "/advanced-chat/workflows/draft/run";
        } else {
            path = "/console/api/apps/" + appId + "/workflows/draft/run";
        }

        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("构造运行请求失败: " + e.getMessage(), e);
        }

        try {
            HttpHeaders headers = authHeaders(auth);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.parseMediaType("text/event-stream"), MediaType.ALL));
            ResponseEntity<byte[]> resp = streamingRestTemplate.postForEntity(
                    url(path), new HttpEntity<>(json, headers), byte[].class);
            HttpStatusCode code = resp.getStatusCode();
            byte[] body = resp.getBody();
            String text = body == null ? "" : new String(body, StandardCharsets.UTF_8);
            if (!code.is2xxSuccessful()) {
                throw new IllegalStateException("Dify 运行返回 " + code.value() + ": " + truncate(text));
            }
            return text;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            if (retried) {
                throw e;
            }
            cachedAuth = null;
            return doDraftRun(appId, advancedChat, query, inputs, true);
        }
    }

    private HttpHeaders authHeaders(Auth auth) {
        HttpHeaders headers = new HttpHeaders();
        if (auth != null) {
            if (auth.token() != null) {
                headers.setBearerAuth(auth.token());
            }
            if (auth.csrf() != null) {
                headers.set("X-CSRF-Token", auth.csrf());
            }
            if (auth.cookieHeader() != null) {
                headers.set(HttpHeaders.COOKIE, auth.cookieHeader());
            }
        }
        return headers;
    }

    private Auth currentAuth() {
        if (props.getAccessToken() != null && !props.getAccessToken().isBlank()) {
            return new Auth(props.getAccessToken(), null, null);
        }
        Auth auth = cachedAuth;
        if (auth != null) {
            return auth;
        }
        synchronized (this) {
            if (cachedAuth == null) {
                cachedAuth = login();
            }
            return cachedAuth;
        }
    }

    private Auth login() {
        // Dify 1.x 登录要求密码 base64 编码（否则报 "Invalid encrypted data"）。
        String encodedPassword = Base64.getEncoder()
                .encodeToString(props.getPassword().getBytes(StandardCharsets.UTF_8));
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", props.getEmail());
        body.put("password", encodedPassword);
        body.put("language", "zh-Hans");
        body.put("remember_me", true);

        ResponseEntity<String> resp = post(url("/console/api/login"), null, body.toString());
        List<String> setCookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);

        // token：优先 JSON body（{data:{access_token}} / {data:"<token>"}），否则取 cookie access_token
        String token = null;
        try {
            JsonNode data = MAPPER.readTree(resp.getBody() == null ? "{}" : resp.getBody()).path("data");
            token = data.isTextual() ? data.asText() : data.path("access_token").asText(null);
        } catch (Exception ignore) {
            // 落到 cookie 兜底
        }
        String cookieAccess = extractCookie(setCookies, "access_token");
        String csrf = extractCookie(setCookies, "csrf_token");
        if (token == null || token.isBlank()) {
            token = cookieAccess;
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("登录成功但未取到 access_token，原始响应: " + truncate(resp.getBody()));
        }

        // Dify 对 POST 有 CSRF 校验，导入时需把 access_token + csrf_token 作为 Cookie 一并带上。
        StringBuilder cookie = new StringBuilder();
        if (cookieAccess != null) {
            cookie.append("access_token=").append(cookieAccess);
        }
        if (csrf != null) {
            if (cookie.length() > 0) {
                cookie.append("; ");
            }
            cookie.append("csrf_token=").append(csrf);
        }
        log.info("[dify-console] login ok (csrf={})", csrf != null);
        return new Auth(token, csrf, cookie.length() > 0 ? cookie.toString() : null);
    }

    /** 从 Set-Cookie 头里取出名字以 {@code name} 结尾的 cookie 值（如 access_token）。 */
    private String extractCookie(List<String> setCookies, String name) {
        if (setCookies == null) {
            return null;
        }
        for (String cookie : setCookies) {
            String first = cookie.split(";", 2)[0].trim();
            int eq = first.indexOf('=');
            if (eq > 0 && first.substring(0, eq).trim().endsWith(name)) {
                return first.substring(eq + 1).trim();
            }
        }
        return null;
    }

    private ResponseEntity<String> post(String url, Auth auth, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (auth != null) {
            if (auth.token() != null) {
                headers.setBearerAuth(auth.token());
            }
            if (auth.csrf() != null) {
                headers.set("X-CSRF-Token", auth.csrf());
            }
            if (auth.cookieHeader() != null) {
                headers.set(HttpHeaders.COOKIE, auth.cookieHeader());
            }
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
