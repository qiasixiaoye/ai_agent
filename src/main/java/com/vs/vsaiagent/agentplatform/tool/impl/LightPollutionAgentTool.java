package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import com.vs.vsaiagent.tools.astro.LightPollutionTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LightPollutionAgentTool extends BaseAgentTool {

    private final LightPollutionTool lightPollutionTool = new LightPollutionTool();

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("light_pollution")
                .displayName("LightPollution")
                .description("估算指定经纬度的光污染等级（启发式 Bortle 等级）")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("astro", "environment"))
                .requiredParams(List.of("latitude", "longitude"))
                .timeoutMs(5000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        var args = request.getArguments();
        double latitude = toDouble(args.get("latitude"));
        double longitude = toDouble(args.get("longitude"));

        String output = lightPollutionTool.getLightPollutionInfo(latitude, longitude);
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
