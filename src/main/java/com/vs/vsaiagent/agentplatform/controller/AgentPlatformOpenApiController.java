package com.vs.vsaiagent.agentplatform.controller;

import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 ToolRegistry 全量导出为 OpenAPI 3.0 spec，与 {@code /skills/openapi.json} 对称，
 * 方便 Dify / 任意外部 Workflow 平台一键导入并作为「自定义工具集合」调用。
 *
 * 输出格式约定：
 *   每个 Tool 暴露为 POST /agent-platform/tools/{toolName}/execute，
 *   request body 为 {@code {"arguments": { ...required params }}}（与现有执行接口一致），
 *   response 200 返回 AgentApiResponse{code,message,data:ToolExecuteResult}。
 *
 * 注意：path 不含 /api 前缀；Dify 后台导入时需把 server.url 设为
 * http://host.docker.internal:8081/api（Dify 在容器内，勿用 localhost）。
 */
@RestController
@RequestMapping("/agent-platform")
public class AgentPlatformOpenApiController {

    private final ToolExecutionService toolExecutionService;

    public AgentPlatformOpenApiController(ToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> openapi() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.0.3");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "vs-ai-agent Agent Tools");
        info.put("description", "Auto-generated from ToolRegistry. Import this in Dify as a custom tool set.");
        info.put("version", "1.0.0");
        root.put("info", info);

        List<Map<String, Object>> servers = new ArrayList<>();
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("url", "http://host.docker.internal:8081/api");
        server.put("description", "vs-ai-agent backend (override before importing; Dify 容器内勿用 localhost)");
        servers.add(server);
        root.put("servers", servers);

        Map<String, Object> paths = new LinkedHashMap<>();
        for (ToolMetadata md : toolExecutionService.listTools()) {
            paths.put("/agent-platform/tools/" + md.getToolName() + "/execute", buildPathItem(md));
        }
        root.put("paths", paths);

        Map<String, Object> components = new LinkedHashMap<>();
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("ToolExecuteResult", toolResultSchema());
        schemas.put("AgentApiResponse", apiResponseSchema());
        components.put("schemas", schemas);
        root.put("components", components);

        return root;
    }

    private Map<String, Object> buildPathItem(ToolMetadata md) {
        Map<String, Object> post = new LinkedHashMap<>();
        post.put("operationId", "tool_" + md.getToolName().replace('-', '_'));
        post.put("summary", md.getDisplayName() == null ? md.getToolName() : md.getDisplayName());
        post.put("description", md.getDescription());
        post.put("tags", md.getTags());

        Map<String, Object> requestBody = new LinkedHashMap<>();
        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, Object> appJson = new LinkedHashMap<>();
        appJson.put("schema", invokeSchema(md));
        content.put("application/json", appJson);
        requestBody.put("required", true);
        requestBody.put("content", content);
        post.put("requestBody", requestBody);

        Map<String, Object> responses = new LinkedHashMap<>();
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("description", "Tool execution result");
        Map<String, Object> okContent = new LinkedHashMap<>();
        Map<String, Object> okJson = new LinkedHashMap<>();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("$ref", "#/components/schemas/AgentApiResponse");
        okJson.put("schema", ref);
        okContent.put("application/json", okJson);
        ok.put("content", okContent);
        responses.put("200", ok);
        post.put("responses", responses);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("post", post);
        return item;
    }

    /** 请求体：{ "arguments": { <required params> } }，与现有 /execute 接口一致。 */
    private Map<String, Object> invokeSchema(ToolMetadata md) {
        Map<String, Object> argProps = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (String param : md.getRequiredParams() == null ? List.<String>of() : md.getRequiredParams()) {
            argProps.put(param, Map.of("type", "string"));
            required.add(param);
        }
        Map<String, Object> argumentsSchema = new LinkedHashMap<>();
        argumentsSchema.put("type", "object");
        argumentsSchema.put("properties", argProps);
        if (!required.isEmpty()) {
            argumentsSchema.put("required", required);
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("arguments", argumentsSchema);
        schema.put("properties", props);
        schema.put("required", List.of("arguments"));
        return schema;
    }

    private Map<String, Object> toolResultSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("toolName", Map.of("type", "string"));
        props.put("success", Map.of("type", "boolean"));
        props.put("output", Map.of("type", "string"));
        props.put("errorMessage", Map.of("type", "string"));
        props.put("costMs", Map.of("type", "integer"));
        schema.put("properties", props);
        return schema;
    }

    private Map<String, Object> apiResponseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("code", Map.of("type", "integer"));
        props.put("message", Map.of("type", "string"));
        props.put("data", Map.of("$ref", "#/components/schemas/ToolExecuteResult"));
        schema.put("properties", props);
        return schema;
    }
}
