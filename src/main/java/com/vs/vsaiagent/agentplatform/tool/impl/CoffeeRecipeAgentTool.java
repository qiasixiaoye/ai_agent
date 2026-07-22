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
 * 咖啡冲煮配方：按冲煮方式给出粉水比、研磨度、水温、时间与步骤。纯规则，演示稳定。
 */
@Component
public class CoffeeRecipeAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("coffee_recipe")
                .displayName("咖啡冲煮配方")
                .description("按冲煮方式（意式 espresso / 手冲 pourover / 法压 french press / 摩卡壶）给出粉水比、研磨度、水温、时间与步骤")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("coffee", "recipe"))
                .requiredParams(List.of("method"))
                .timeoutMs(5000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> a = request.getArguments();
        String method = String.valueOf(a.getOrDefault("method", "")).toLowerCase();
        int cups = (int) toDouble(a.get("cups"), 1);

        String name, ratio, grind, temp, time, steps;
        if (contains(method, "意式", "espresso", "浓缩")) {
            name = "意式浓缩 Espresso";
            ratio = "1:2（18g 粉 → 36g 液）"; grind = "极细"; temp = "92-94°C"; time = "25-30s";
            steps = "布粉均匀 → 压粉 30lb → 9bar 萃取 → 看流速金黄油脂(crema)。";
        } else if (contains(method, "法压", "french")) {
            name = "法压壶 French Press";
            ratio = "1:15"; grind = "粗"; temp = "93°C"; time = "4 分钟";
            steps = "粉入壶 → 注水闷蒸 → 4 分钟后压下滤网 → 立即分离避免过萃。";
        } else if (contains(method, "摩卡", "moka")) {
            name = "摩卡壶 Moka Pot";
            ratio = "下壶满水至安全阀，粉斗装平不压"; grind = "中细"; temp = "热水起冲"; time = "上汽即停";
            steps = "中小火 → 听到咕噜声立刻离火 → 冷水降温防苦。";
        } else {
            name = "手冲 Pour Over";
            ratio = "1:16（15g 粉 → 240g 水）"; grind = "中"; temp = "92-94°C"; time = "2:30-3:00";
            steps = "30g 水闷蒸 30s → 分 2-3 段绕圈注水 → 水位下降再续，总时 2:30-3:00。";
        }

        String output = String.format(
                "【%s】(约 %d 杯)\n粉水比：%s\n研磨度：%s ｜ 水温：%s ｜ 时间：%s\n步骤：%s",
                name, cups, ratio, grind, temp, time, steps);
        return ToolExecuteResult.builder()
                .toolName(toolName()).success(true).output(output)
                .costMs(System.currentTimeMillis() - start).build();
    }

    private boolean contains(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private double toDouble(Object v, double dft) {
        if (v == null) return dft;
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return dft; }
    }
}
