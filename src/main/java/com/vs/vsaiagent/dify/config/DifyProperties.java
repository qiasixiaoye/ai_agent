package com.vs.vsaiagent.dify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Dify 集成配置。
 *
 * 在 application-*.yml 里：
 *   app:
 *     dify:
 *       base-url: https://api.dify.ai      # 也可以指向自建 Dify
 *       api-key: app-xxxxx                 # Dify Workflow App 的 API Key
 *       default-workflow-id: ""            # 可选默认 workflow id
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.dify")
public class DifyProperties {
    private String baseUrl;
    private String apiKey;
    private String defaultWorkflowId;
    private Integer timeoutMs = 60000;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }
}
