package com.vs.vsaiagent.agentplatform.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.tool.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 {@link AgentTool} 适配成 Spring AI {@link ToolCallback}，从而能通过 MCP Server
 * 暴露给 Dify 等外部平台动态调用（与 {@link com.vs.vsaiagent.skill.adapter.SkillCallbackAdapter}
 * 对称）。
 *
 * 适配点：
 *  - getToolDefinition() 用 {@link ToolMetadata} 构造，inputSchema 由 requiredParams 拼成 JSON Schema
 *  - call(String) 把 JSON 入参反序列化为 Map → 包成 {@link ToolExecuteRequest} → 调 AgentTool.execute
 *  - 结果 {@link ToolExecuteResult} 序列化为 JSON 字符串返回
 */
@Slf4j
public class AgentToolCallback implements ToolCallback {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentTool tool;
    private final ToolDefinition definition;

    public AgentToolCallback(AgentTool tool) {
        this.tool = tool;
        this.definition = buildDefinition(tool.metadata());
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
        ToolExecuteRequest request = ToolExecuteRequest.builder()
                .toolName(tool.toolName())
                .arguments(args)
                .build();
        try {
            tool.validate(request);
            ToolExecuteResult result = tool.execute(request);
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[agent-tool-adapter] {} 执行失败: {}", tool.toolName(), e.getMessage());
            return "{\"success\":false,\"errorMessage\":\"" + safe(e.getMessage()) + "\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[agent-tool-adapter] parse tool input failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private static ToolDefinition buildDefinition(ToolMetadata md) {
        String description = md.getDescription() == null ? md.getDisplayName() : md.getDescription();
        return ToolDefinition.builder()
                .name(md.getToolName())
                .description(description == null ? md.getToolName() : description)
                .inputSchema(toJsonSchema(md))
                .build();
    }

    /** 把 requiredParams 拼成最小可用 JSON Schema（每个参数默认 string 类型）。 */
    private static String toJsonSchema(ToolMetadata md) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");

        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (String param : md.getRequiredParams() == null ? List.<String>of() : md.getRequiredParams()) {
            props.put(param, Map.of("type", "string"));
            required.add(param);
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

    private static String safe(String s) {
        return s == null ? "error" : s.replace("\"", "'");
    }
}
