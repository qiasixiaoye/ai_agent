package com.vs.vsaiagent.dify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Dify Console API 配置（用于 DSL 自动导入，区别于运行用的 app api-key）。
 *
 * app:
 *   dify:
 *     console:
 *       base-url: http://localhost:3001   # 官方 Dify Web/Nginx 入口
 *       email: admin@example.com          # 控制台登录账号
 *       password: xxx
 *       access-token:                     # 可选：直接给 token 则跳过登录
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.dify.console")
public class DifyConsoleProperties {

    private String baseUrl;
    private String email;
    private String password;
    private String accessToken;

    public boolean isConfigured() {
        boolean hasAuth = (accessToken != null && !accessToken.isBlank())
                || (email != null && !email.isBlank() && password != null && !password.isBlank());
        return baseUrl != null && !baseUrl.isBlank() && hasAuth;
    }
}
