package com.vs.vsaiagent.skill.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.vsaiagent.observability.context.TraceContext;
import com.vs.vsaiagent.observability.context.TraceInfo;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.SkillResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 {@link Skill} 适配成 Spring AI {@link ToolCallback}，使其可以直接被
 * ChatClient.tools(...) 注入；从而 AssistantApp / VsManus 无需感知 Skill 抽象。
 *
 * 适配点：
 *  - getToolDefinition() 用 SkillMetadata 构造，inputSchema 由 SkillParam 拼装为 JSON Schema
 *  - call(String) / call(String, ToolContext) 把 JSON 入参反序列化为 Map，再透传给 Skill.execute
 *  - 自动从 TraceContext 抽出 traceId/requestId/sessionId 填充 SkillContext
 */
@Slf4j
public class SkillCallbackAdapter implements ToolCallback {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Skill skill;
    private final ToolDefinition definition;

    public SkillCallbackAdapter(Skill skill) {
        this.skill = skill;
        this.definition = buildDefinition(skill.metadata());
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        return doCall(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return doCall(toolInput);
    }

    private String doCall(String toolInput) {
        Map<String, Object> args = parse(toolInput);
        SkillContext ctx = buildSkillContext();
        SkillResult result = skill.execute(args, ctx);
        try {
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            log.error("[skill-adapter] serialize result of {} failed", skill.name(), e);
            return "{\"success\":false,\"errorMessage\":\"serialize_failed\"}";
        }
    }

    private SkillContext buildSkillContext() {
        TraceInfo info = TraceContext.get();
        SkillContext.Builder b = SkillContext.builder();
        if (info != null) {
            b.traceId(info.traceId()).requestId(info.requestId()).sessionId(info.sessionId());
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[skill-adapter] parse tool input failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private static ToolDefinition buildDefinition(SkillMetadata md) {
        return ToolDefinition.builder()
                .name(md.name())
                .description(md.description() == null ? md.displayName() : md.description())
                .inputSchema(toJsonSchema(md))
                .build();
    }

    /** 把 SkillMetadata.inputs() 拼装成最小可用的 JSON Schema 字符串。 */
    private static String toJsonSchema(SkillMetadata md) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");

        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (SkillParam p : md.inputs()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", p.type() == null ? "string" : p.type());
            if (p.description() != null) prop.put("description", p.description());
            props.put(p.name(), prop);
            if (p.required()) required.add(p.name());
        }
        root.put("properties", props);
        if (!required.isEmpty()) {
            root.put("required", required);
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"type\":\"object\"}";
        }
    }
}
