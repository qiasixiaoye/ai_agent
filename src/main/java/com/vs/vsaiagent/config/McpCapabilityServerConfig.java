package com.vs.vsaiagent.config;

import com.vs.vsaiagent.agentplatform.adapter.AgentToolCallback;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.registry.ToolRegistry;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import com.vs.vsaiagent.observability.tool.McpToolLoggingCallback;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.adapter.SkillCallbackAdapter;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 ToolRegistry + SkillRegistry 里已注册的能力，作为 MCP tools 暴露出去
 * （依赖 spring-ai-mcp-server-webmvc-starter，默认走 SSE）。Dify 等平台注册一次
 * 本服务的 MCP 端点后，即可在运行时动态拉取这份工具清单——新增能力后重启服务即可
 * 在 Dify 刷新可见，无需在 Dify 侧重新导入（区别于 OpenAPI 快照导入）。
 *
 * 命名为 {@code capabilityToolCallbackProvider}，与 MCP Client starter 默认的
 * {@code toolCallbackProvider} 区分开：AssistantApp(@Resource by name) 与
 * McpRoutingAgentTool(@Autowired by field name) 仍按字段名解析到 client 的那个 Bean，
 * 避免类型歧义。MCP Server 自动配置会收集全部 ToolCallbackProvider，因此本 Bean 会被一并暴露。
 */
@Configuration
public class McpCapabilityServerConfig {

    @Bean
    public ToolCallbackProvider capabilityToolCallbackProvider(ToolRegistry toolRegistry,
                                                               SkillRegistry skillRegistry,
                                                               ExecutionLogService executionLogService) {
        // 每次被调用时实时读取注册表，反映当前已配置能力（而非启动时快照）。
        // 每个 callback 再包一层 McpToolLoggingCallback：外部平台（Dify 等）经 MCP 调过来的
        // 工具调用，自起一条 mcp-tool-call request 落审计，填补运行时工具调用不可观测的盲区。
        return () -> {
            List<ToolCallback> callbacks = new ArrayList<>();
            for (ToolMetadata md : toolRegistry.listMetadata()) {
                toolRegistry.findByName(md.getToolName())
                        .ifPresent(tool -> callbacks.add(
                                new McpToolLoggingCallback(new AgentToolCallback(tool), executionLogService)));
            }
            for (Skill skill : skillRegistry.listAll()) {
                callbacks.add(new McpToolLoggingCallback(new SkillCallbackAdapter(skill), executionLogService));
            }
            return callbacks.toArray(new ToolCallback[0]);
        };
    }
}
