package com.vs.vsaiagent.agentplatform.tool.impl;

import cn.hutool.json.JSONUtil;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import com.vs.vsaiagent.mcp.management.ManagedMcpToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class McpRoutingAgentTool extends BaseAgentTool {

    private final ChatClient chatClient;
    private final ManagedMcpToolService managedMcpToolService;

    public McpRoutingAgentTool(ChatModel chatModel, ManagedMcpToolService managedMcpToolService) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.managedMcpToolService = managedMcpToolService;
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("mcp_router")
                .displayName("McpRouter")
                .description("MCP 工具路由代理")
                .sourceType(ToolSourceType.MCP)
                .tags(List.of("mcp", "tool-calling"))
                .requiredParams(List.of("instruction"))
                .timeoutMs(15000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        ToolCallback[] callbacks = managedMcpToolService.allowedToolCallbacks();
        if (callbacks.length == 0) throw new IllegalStateException("当前没有可用且通过策略的 MCP 工具");
        long start = System.currentTimeMillis();
        Map<String, Object> args = request.getArguments();
        String instruction = String.valueOf(args.get("instruction"));
        String argumentJson = JSONUtil.toJsonStr(args.getOrDefault("toolArgs", Map.of()));
        String output = chatClient.prompt()
                .user("""
                        请严格使用 MCP 工具完成任务，不要回答解释。
                        任务说明: %s
                        工具参数(JSON): %s
                        仅返回最终工具执行结果。
                        """.formatted(instruction, argumentJson))
                .tools(callbacks)
                .call()
                .content();
        return ToolExecuteResult.builder()
                .toolName(toolName())
                .success(true)
                .output(output)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }
}
