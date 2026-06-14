package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import com.vs.vsaiagent.tools.astro.MilkyWayRiseTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MilkyWayRiseAgentTool extends BaseAgentTool {

    private final MilkyWayRiseTool milkyWayRiseTool = new MilkyWayRiseTool();

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("milkyway_rise")
                .displayName("MilkyWayRise")
                .description("计算银河核心升起/中天/落下时间与方位角")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("astro", "calculation"))
                .requiredParams(List.of("latitude", "longitude", "date"))
                .timeoutMs(5000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        var args = request.getArguments();
        double latitude = toDouble(args.get("latitude"));
        double longitude = toDouble(args.get("longitude"));
        String date = String.valueOf(args.get("date"));
        Object tzRaw = args.get("timezoneOffset");
        Double timezoneOffset = tzRaw == null ? null : toDouble(tzRaw);

        String output = milkyWayRiseTool.getMilkyWayCoreInfo(latitude, longitude, date, timezoneOffset);
        return ToolExecuteResult.builder()
                .toolName(toolName())
                .success(true)
                .output(output)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }

    private double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }
}
