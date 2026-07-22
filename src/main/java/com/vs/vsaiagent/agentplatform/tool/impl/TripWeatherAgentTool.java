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
 * 行程天气概览：按出发地、目的地与月份，给出沿途气温区间、昼夜温差、降雨概率与穿衣/骑行建议。
 * 纯规则推算（不联网），演示稳定；用于让"摩旅/长途出行"一句话需求自然展开出多个工具节点。
 */
@Component
public class TripWeatherAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("trip_weather")
                .displayName("行程天气概览")
                .description("按出发地/目的地/月份估算沿途气温区间、昼夜温差、降雨概率，并给出穿衣与骑行天气提示")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("travel", "weather"))
                .requiredParams(List.of("destination"))
                .timeoutMs(5000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> a = request.getArguments();
        String from = str(a.get("from"), "出发地");
        String to = str(a.get("destination"), "目的地");
        int month = (int) toDouble(a.get("month"), monthNow());

        boolean plateau = to.contains("西藏") || to.contains("拉萨") || to.contains("青海")
                || to.contains("新疆") || to.contains("高原") || to.contains("川西");
        boolean summer = month >= 6 && month <= 8;
        boolean winter = month == 12 || month <= 2;

        int lowC = plateau ? (summer ? 5 : -10) : (summer ? 22 : (winter ? 0 : 12));
        int highC = plateau ? (summer ? 20 : 5) : (summer ? 34 : (winter ? 10 : 24));
        int diurnal = highC - lowC;
        int rainProb = summer ? (plateau ? 55 : 45) : 20;

        String advice = plateau
                ? "高原昼夜温差大，必须可拆卸内胆/保暖分层 + 防风；午后多阵雨，备两件套雨衣；防晒(雪盲镜/高倍防晒)"
                : (summer ? "高温注意补水防中暑、午后雷阵雨备雨具" : (winter ? "低温注意防风保暖、警惕路面结冰" : "温差适中，早晚加一件薄外套"));

        String output = String.format(
                "行程天气概览（%s → %s · %d 月）：\n"
              + "· 气温区间约 %d~%d℃，昼夜温差约 %d℃\n"
              + "· 降雨概率约 %d%%\n"
              + "· 建议：%s",
                from, to, month, lowC, highC, diurnal, rainProb, advice);
        return ToolExecuteResult.builder()
                .toolName(toolName()).success(true).output(output)
                .costMs(System.currentTimeMillis() - start).build();
    }

    private int monthNow() {
        return java.time.LocalDate.now().getMonthValue();
    }

    private String str(Object v, String dft) {
        if (v == null) return dft;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? dft : s;
    }

    private double toDouble(Object v, double dft) {
        if (v == null) return dft;
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return dft; }
    }
}
