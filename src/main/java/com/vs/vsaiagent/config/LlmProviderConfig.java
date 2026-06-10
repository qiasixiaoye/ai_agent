package com.vs.vsaiagent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmProviderConfig {

    @Bean(name = "primaryDashscopeChatModel")
    @Primary
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "dashscope", matchIfMissing = true)
    public ChatModel primaryDashscopeChatModel(@Qualifier("dashscopeChatModel") ChatModel dashscopeChatModel) {
        return dashscopeChatModel;
    }

    @Bean(name = "deepseekChatModel")
    @Primary
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "deepseek")
    public ChatModel deepseekChatModel(@Qualifier("openAiChatModel") ChatModel openAiChatModel) {
        return openAiChatModel;
    }
}
