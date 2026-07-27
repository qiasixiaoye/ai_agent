package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Deterministic read-only data adapter for the enterprise field-research demonstration. */
@Component
public class FieldWeatherSummaryAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("field_weather_summary")
                .displayName("外勤天气摘要")
                .description("使用可追溯的演示数据生成上午和下午外勤天气建议")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("field-research", "weather", "read-only"))
                .requiredParams(List.of("location"))
                .timeoutMs(1500L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        String location = String.valueOf(request.getArguments().get("location")).trim();
        if (Boolean.TRUE.equals(request.getArguments().get("simulateFailure"))) {
            return ToolExecuteResult.builder().toolName(toolName()).success(false)
                    .errorMessage("unavailable: 天气演示数据适配器不可用，未推断天气结论")
                    .costMs(System.currentTimeMillis() - start).build();
        }
        String observedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String output = "来源：演示数据；获取时间：" + observedAt + "\n"
                + "地点：" + location + "\n"
                + "上午：建议在 09:30-11:30 安排拜访，携带轻便雨具并预留 20 分钟通勤缓冲。\n"
                + "下午：建议优先室内会议；若外出，避开 16:00 后的交通高峰。\n"
                + "限制：该结果是稳定演示数据，不代表实时天气预报。";
        return ToolExecuteResult.builder().toolName(toolName()).success(true).output(output)
                .costMs(System.currentTimeMillis() - start).build();
    }
}
