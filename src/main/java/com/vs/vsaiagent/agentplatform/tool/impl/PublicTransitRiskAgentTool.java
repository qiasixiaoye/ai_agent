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

/** Read-only transit-risk demonstration adapter; it never turns missing data into a safety claim. */
@Component
public class PublicTransitRiskAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("public_transit_risk")
                .displayName("公共出行风险")
                .description("使用演示数据给出公共出行时间缓冲建议")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("field-research", "transit", "read-only"))
                .requiredParams(List.of("location"))
                .timeoutMs(1500L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        if (Boolean.TRUE.equals(request.getArguments().get("simulateFailure"))) {
            return ToolExecuteResult.builder().toolName(toolName()).success(false)
                    .errorMessage("unavailable: 未取得公共出行风险数据；请预留额外通勤时间，不对风险等级作判断")
                    .costMs(System.currentTimeMillis() - start).build();
        }
        String location = String.valueOf(request.getArguments().get("location")).trim();
        String observedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String output = "来源：演示数据；获取时间：" + observedAt + "\n"
                + "地点：" + location + "\n"
                + "建议：上午首个拜访前预留 20 分钟；下午 16:00 后减少跨区移动。\n"
                + "限制：该结果不是实时道路、地铁或治安信息。";
        return ToolExecuteResult.builder().toolName(toolName()).success(true).output(output)
                .costMs(System.currentTimeMillis() - start).build();
    }
}
