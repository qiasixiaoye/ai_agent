package com.vs.vsaiagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 取代 server.servlet.context-path=/api：仅对本项目注解式 controller 加 /api 前缀，
 * 不影响 Spring AI MCP server 的函数式 RouterFunction（/sse、/mcp/message 留在根路径），
 * 从而让 MCP 在 SSE 握手里通告的消息端点与实际服务端点一致，修复 Dify 挂载 MCP 服务。
 *
 * 背景：WebMvcSseServerTransport 用同一个 messageEndpoint 字符串既注册 POST 路由
 * （会被 context-path 再加前缀 → /api/mcp/message），又原样通告给客户端（不带 context-path
 * → /mcp/message），两者错位导致 Dify 握手 404。改为前缀方式后 MCP 端点不再被 context-path 影响。
 */
@Configuration
public class ApiPathPrefixConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",
                HandlerTypePredicate.forBasePackage("com.vs.vsaiagent"));
    }
}
