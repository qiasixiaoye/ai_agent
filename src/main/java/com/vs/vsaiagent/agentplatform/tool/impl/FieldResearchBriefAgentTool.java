package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Aggregates only already collected evidence; it does not invent missing facts. */
@Component
public class FieldResearchBriefAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("field_research_brief")
                .displayName("外勤调研建议汇总")
                .description("基于天气和公共出行证据生成可审计的外勤建议")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("field-research", "brief", "read-only"))
                .requiredParams(List.of("weatherEvidence", "transitEvidence"))
                .timeoutMs(1500L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> args = request.getArguments();
        String weather = String.valueOf(args.get("weatherEvidence"));
        String transit = String.valueOf(args.get("transitEvidence"));
        StringBuilder output = new StringBuilder("【外勤调研建议】\n")
                .append("结论：上午优先安排首个客户拜访，下午减少跨区移动；以下建议仅依据列出的演示证据。\n")
                .append("天气证据：").append(weather).append("\n")
                .append("出行证据：").append(transit).append("\n");
        if (weather.contains("unavailable") || transit.contains("unavailable")) {
            output.append("限制：至少一项数据不可用，未对缺失信息作安全或天气推断；请在出发前复核实时渠道。\n");
        }
        return ToolExecuteResult.builder().toolName(toolName()).success(true).output(output.toString())
                .costMs(System.currentTimeMillis() - start).build();
    }
}
