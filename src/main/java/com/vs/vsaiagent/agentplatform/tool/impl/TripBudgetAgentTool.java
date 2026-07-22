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
 * 出行预算估算：按总里程、天数与油耗，分项估算油费、食宿、过路/门票与应急储备，给出总预算区间。
 * 纯计算，无外部依赖，演示稳定。
 */
@Component
public class TripBudgetAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("trip_budget")
                .displayName("出行预算估算")
                .description("按总里程(km)、天数与油耗分项估算油费、食宿、过路门票与应急储备，给出总预算区间")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("travel", "budget"))
                .requiredParams(List.of("distanceKm"))
                .timeoutMs(5000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> a = request.getArguments();
        double distance = toDouble(a.get("distanceKm"), 1000);
        int days = (int) toDouble(a.get("days"), 5);
        double fuelPer100 = toDouble(a.get("fuelPer100km"), 4);   // L/100km，摩托默认 4
        double fuelPrice = toDouble(a.get("fuelPrice"), 8);       // 元/L
        double lodgingPerDay = toDouble(a.get("lodgingPerDay"), 200);
        double mealPerDay = toDouble(a.get("mealPerDay"), 100);

        double fuel = distance / 100.0 * fuelPer100 * fuelPrice;
        double lodging = lodgingPerDay * Math.max(0, days - 1);   // 最后一天到家
        double meal = mealPerDay * days;
        double tollTicket = distance * 0.15 + days * 50;          // 过路/门票粗估
        double base = fuel + lodging + meal + tollTicket;
        double reserve = base * 0.15;                             // 15% 应急储备
        double low = base + reserve;
        double high = (base + reserve) * 1.25;

        String output = String.format(
                "出行预算估算（%.0f km · %d 天）：\n"
              + "· 油费：¥%.0f（%.1fL/100km × ¥%.1f/L）\n"
              + "· 食宿：¥%.0f（住 ¥%.0f/晚 + 餐 ¥%.0f/天）\n"
              + "· 过路/门票：¥%.0f\n"
              + "· 应急储备(15%%)：¥%.0f\n"
              + "· 预算区间：¥%.0f ~ ¥%.0f",
                distance, days, fuel, fuelPer100, fuelPrice,
                lodging + meal, lodgingPerDay, mealPerDay, tollTicket, reserve, low, high);
        return ToolExecuteResult.builder()
                .toolName(toolName()).success(true).output(output)
                .costMs(System.currentTimeMillis() - start).build();
    }

    private double toDouble(Object v, double dft) {
        if (v == null) return dft;
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return dft; }
    }
}
