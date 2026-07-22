package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 星空拍摄参数推荐：按 500 法则根据镜头焦距、光圈、光污染 Bortle 等级，
 * 推荐银河摄影的最长快门、ISO、光圈与构图建议。纯计算、无外部依赖，演示稳定。
 *
 * 自动注册：作为 {@link com.vs.vsaiagent.agentplatform.tool.AgentTool} 的
 * {@code @Component}，由 ToolRegistryInitializer 启动时注册，进而出现在
 * /agent-platform/tools、/agent-platform/openapi.json 与 MCP server 工具清单中。
 */
@Component
public class AstrophotoSettingsAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("astrophoto_settings")
                .displayName("星空拍摄参数")
                .description("按 500 法则根据镜头焦距、光圈、光污染 Bortle 等级推荐快门/ISO/光圈与构图建议")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("astro", "photography"))
                .requiredParams(List.of("focalLength"))
                .timeoutMs(5000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> a = request.getArguments();
        double focal = toDouble(a.get("focalLength"), 24);
        double aperture = toDouble(a.get("aperture"), 2.8);
        int bortle = (int) toDouble(a.get("bortle"), 4);

        // 500 法则：最长快门 ≈ 500 / 焦距，避免星点拖线
        double shutter = Math.round((500.0 / focal) * 10) / 10.0;
        // 光污染越重(Bortle 越大)，ISO 越保守
        int iso = bortle <= 3 ? 3200 : (bortle <= 5 ? 1600 : 800);

        String output = String.format(
                "建议参数：快门 %ss（500法则，防拖线）｜光圈 f/%.1f（开到最大进光）｜ISO %d（Bortle %d）。\n"
              + "操作：对焦无穷远、关防抖、2 秒延时或快门线、RAW 格式、对准银河中心（夏季人马座方向）。",
                trim(shutter), aperture, iso, bortle);

        return ToolExecuteResult.builder()
                .toolName(toolName())
                .success(true)
                .output(output)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }

    private double toDouble(Object v, double dft) {
        if (v == null) {
            return dft;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return dft;
        }
    }

    private String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
