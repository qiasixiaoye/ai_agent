package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import com.vs.vsaiagent.tools.astro.CloudCoverTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CloudCoverAgentTool extends BaseAgentTool {

    private final CloudCoverTool cloudCoverTool = new CloudCoverTool();

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("cloud_cover")
                .displayName("CloudCover")
                .description("查询指定经纬度和日期夜间云量预报")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("astro", "weather"))
                .requiredParams(List.of("latitude", "longitude", "date"))
                .timeoutMs(8000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        var args = request.getArguments();
        double latitude = toDouble(args.get("latitude"));
        double longitude = toDouble(args.get("longitude"));
        String date = String.valueOf(args.get("date"));

        String output = cloudCoverTool.getCloudCoverInfo(latitude, longitude, date);
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
