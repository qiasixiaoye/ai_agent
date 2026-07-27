package com.vs.vsaiagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import com.vs.vsaiagent.observability.context.TraceContext;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("manusAgentExecutor")
    public SimpleAsyncTaskExecutor manusAgentExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("manus-agent-");
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(TraceContext::wrap);
        return executor;
    }
}
