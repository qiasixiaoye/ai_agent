package com.vs.vsaiagent.agentplatform.tool.impl;

import cn.hutool.core.util.StrUtil;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResultSummaryAgentTool extends BaseAgentTool {

    private final ChatClient chatClient;

    public ResultSummaryAgentTool(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("result_summary")
                .displayName("ResultSummary")
                .description("结果汇总工具")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("summary", "llm"))
                .requiredParams(List.of("searchResult", "imageResult"))
                .timeoutMs(10000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        String searchResult = String.valueOf(request.getArguments().get("searchResult"));
        String imageResult = String.valueOf(request.getArguments().get("imageResult"));
        if (StrUtil.isBlank(searchResult) && StrUtil.isBlank(imageResult)) {
            throw new IllegalArgumentException("汇总输入不能为空");
        }
        String output = chatClient.prompt()
                .user("""
                        请将以下检索结果整理为结构化总结：
                        1) 文本检索结果：%s
                        2) 图片检索结果：%s
                        输出字段：核心结论、关键信息、图片建议。
                        """.formatted(searchResult, imageResult))
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
