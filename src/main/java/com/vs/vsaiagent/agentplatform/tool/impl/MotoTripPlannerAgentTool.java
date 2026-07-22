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
 * 摩托旅行行程规划：按总里程与每日骑行时长估算天数、加油点、休息节奏与安全提示。
 * 纯计算，无外部依赖，演示稳定。
 */
@Component
public class MotoTripPlannerAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("moto_trip_planner")
                .displayName("摩旅行程规划")
                .description("按总里程(km)与每日骑行时长估算所需天数、加油点数、休息节奏，并给出安全与装备提示")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("motorcycle", "travel"))
                .requiredParams(List.of("distanceKm"))
                .timeoutMs(5000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> a = request.getArguments();
        double distance = toDouble(a.get("distanceKm"), 500);
        double hoursPerDay = toDouble(a.get("hoursPerDay"), 6);
        double avgSpeed = toDouble(a.get("avgSpeed"), 65);   // 含休息的综合均速
        double tankRangeKm = toDouble(a.get("tankRangeKm"), 250);

        double dailyKm = Math.max(1, hoursPerDay * avgSpeed);
        int days = (int) Math.ceil(distance / dailyKm);
        int fuelStops = (int) Math.ceil(distance / tankRangeKm);
        // 每骑行约 2 小时休息一次
        int restsPerDay = (int) Math.max(1, Math.round(hoursPerDay / 2));

        String output = String.format(
                "摩旅规划（总里程 %.0f km）：\n"
              + "· 预计 %d 天（每日约 %.0f km / %.0f 小时，综合均速 %.0f km/h）\n"
              + "· 加油点约 %d 次（油箱续航 ~%.0f km）\n"
              + "· 每日休息 ~%d 次（每骑行 2h 停 10-15 分钟，活动颈肩手腕）\n"
              + "· 安全：日落前 1 小时收车、避免夜骑山路、雨天降速、ATGATT 全套护具\n"
              + "· 装备：边箱防水内袋、补胎工具+打气、备用手套、保暖分层、手机支架+充电",
                distance, days, dailyKm, hoursPerDay, avgSpeed, fuelStops, tankRangeKm, restsPerDay);
        return ToolExecuteResult.builder()
                .toolName(toolName()).success(true).output(output)
                .costMs(System.currentTimeMillis() - start).build();
    }

    private double toDouble(Object v, double dft) {
        if (v == null) return dft;
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return dft; }
    }
}
