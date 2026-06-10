package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import com.vs.vsaiagent.tools.ImageSearchTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LocalImageSearchAgentTool extends BaseAgentTool {

    private final ImageSearchTool imageSearchTool;

    public LocalImageSearchAgentTool(@Value("${image-api.api-key:}") String apiKey) {
        this.imageSearchTool = new ImageSearchTool(apiKey);
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("image_search")
                .displayName("ImageSearch")
                .description("图片检索工具")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("image", "retrieval"))
                .requiredParams(List.of("query"))
                .timeoutMs(12000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        String query = String.valueOf(request.getArguments().get("query"));
        String output = imageSearchTool.searchImage(query);
        return ToolExecuteResult.builder()
                .toolName(toolName())
                .success(true)
                .output(output)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }
}
