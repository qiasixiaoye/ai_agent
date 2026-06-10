package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import com.vs.vsaiagent.tools.WebSearchTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LocalWebSearchAgentTool extends BaseAgentTool {

    private final WebSearchTool webSearchTool;

    public LocalWebSearchAgentTool(@Value("${search-api.api-key:}") String apiKey) {
        this.webSearchTool = new WebSearchTool(apiKey);
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("web_search")
                .displayName("WebSearch")
                .description("网页检索工具")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("search", "retrieval"))
                .requiredParams(List.of("query"))
                .timeoutMs(12000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        String query = String.valueOf(request.getArguments().get("query"));
        String output = webSearchTool.searchWeb(query);
        return ToolExecuteResult.builder()
                .toolName(toolName())
                .success(true)
                .output(output)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }
}
