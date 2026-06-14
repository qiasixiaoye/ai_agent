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
public class AstroPlanSummaryAgentTool extends BaseAgentTool {

    private final ChatClient chatClient;

    public AstroPlanSummaryAgentTool(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("astro_plan_summary")
                .displayName("AstroPlanSummary")
                .description("汇总银心可见性、光污染、云量数据，生成银河摄影拍摄建议")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("astro", "summary", "llm"))
                .requiredParams(List.of("milkyWayResult", "lightPollutionResult", "cloudCoverResult"))
                .timeoutMs(15000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        var args = request.getArguments();
        String milkyWayResult = String.valueOf(args.get("milkyWayResult"));
        String lightPollutionResult = String.valueOf(args.get("lightPollutionResult"));
        String cloudCoverResult = String.valueOf(args.get("cloudCoverResult"));
        String location = String.valueOf(args.getOrDefault("location", ""));
        String date = String.valueOf(args.getOrDefault("date", ""));

        if (StrUtil.isBlank(milkyWayResult) && StrUtil.isBlank(lightPollutionResult) && StrUtil.isBlank(cloudCoverResult)) {
            throw new IllegalArgumentException("汇总输入不能为空");
        }

        String output = chatClient.prompt()
                .user("""
                        你是一名银河摄影规划助手。请基于以下三项数据，为用户生成一份银河拍摄建议：
                        1) 银心可见性数据（升起/中天/落下时间与方位角）：%s
                        2) 光污染估算：%s
                        3) 夜间云量预报：%s
                        位置：%s  日期：%s

                        输出字段：
                        - 是否建议拍摄（是/否/视情况）
                        - 最佳拍摄时间窗口
                        - 拍摄方向建议（朝向方位角）
                        - 风险提示（光污染/云量）
                        - 简要总结
                        """.formatted(milkyWayResult, lightPollutionResult, cloudCoverResult, location, date))
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
