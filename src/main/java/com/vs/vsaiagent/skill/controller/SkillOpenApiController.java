package com.vs.vsaiagent.skill.controller;

import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 SkillRegistry 全量导出为 OpenAPI 3.0 spec，方便 Dify / Cursor / 任意外部
 * Workflow 平台一键导入并作为「自定义工具集合」调用。
 *
 * 输出格式约定：
 *   每个 Skill 暴露为 POST /skills/{name}/execute，request body 是 JSON object
 *   字段来自 SkillMetadata.inputs()；response 200 返回 SkillResult。
 *
 * 注意：因为前端 baseURL 已带 /api，这里返回的 path 不再加 /api 前缀；
 * Dify 后台导入时需要把 server.url 设为 http://host:8081/api。
 */
@RestController
@RequestMapping("/skills")
public class SkillOpenApiController {

    private final SkillRegistry skillRegistry;

    public SkillOpenApiController(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> openapi() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.0.3");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "vs-ai-agent Skills");
        info.put("description", "Auto-generated from SkillRegistry. Import this in Dify as a custom tool set.");
        info.put("version", "1.0.0");
        root.put("info", info);

        List<Map<String, Object>> servers = new ArrayList<>();
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("url", "http://localhost:8081/api");
        server.put("description", "vs-ai-agent backend (override before importing)");
        servers.add(server);
        root.put("servers", servers);

        Map<String, Object> paths = new LinkedHashMap<>();
        for (Skill s : skillRegistry.listAll()) {
            SkillMetadata md = s.metadata();
            paths.put("/skills/" + md.name() + "/execute", buildPathItem(md));
        }
        root.put("paths", paths);

        // 公共结果 schema
        Map<String, Object> components = new LinkedHashMap<>();
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("SkillResult", skillResultSchema());
        components.put("schemas", schemas);
        root.put("components", components);

        return root;
    }

    private Map<String, Object> buildPathItem(SkillMetadata md) {
        Map<String, Object> post = new LinkedHashMap<>();
        post.put("operationId", "skill_" + md.name().replace('-', '_'));
        post.put("summary", md.displayName() == null ? md.name() : md.displayName());
        post.put("description", md.description());
        post.put("tags", md.tags());

        // requestBody
        Map<String, Object> requestBody = new LinkedHashMap<>();
        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, Object> appJson = new LinkedHashMap<>();
        appJson.put("schema", inputSchema(md));
        content.put("application/json", appJson);
        requestBody.put("required", true);
        requestBody.put("content", content);
        post.put("requestBody", requestBody);

        // responses
        Map<String, Object> responses = new LinkedHashMap<>();
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("description", "Skill execution result");
        Map<String, Object> okContent = new LinkedHashMap<>();
        Map<String, Object> okJson = new LinkedHashMap<>();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("$ref", "#/components/schemas/SkillResult");
        okJson.put("schema", ref);
        okContent.put("application/json", okJson);
        ok.put("content", okContent);
        responses.put("200", ok);
        post.put("responses", responses);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("post", post);
        return item;
    }

    private Map<String, Object> inputSchema(SkillMetadata md) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (SkillParam p : md.inputs()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", p.type() == null ? "string" : p.type());
            if (p.description() != null) prop.put("description", p.description());
            props.put(p.name(), prop);
            if (p.required()) required.add(p.name());
        }
        schema.put("properties", props);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }

    private Map<String, Object> skillResultSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("success", Map.of("type", "boolean"));
        props.put("data", Map.of("type", "object"));
        props.put("errorMessage", Map.of("type", "string"));
        props.put("elapsedMs", Map.of("type", "integer"));
        schema.put("properties", props);
        return schema;
    }
}
