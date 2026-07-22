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
 * 沿途景点/打卡点推荐：按目的地（或路线关键词）给出沿途值得停留的景点、打卡点与停留建议。
 * 内置几条经典线路，未命中时给出通用推荐策略。纯规则，演示稳定。
 */
@Component
public class ScenicSpotsAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("scenic_spots")
                .displayName("沿途景点推荐")
                .description("按目的地或路线给出沿途值得停留的景点/打卡点与停留时长建议")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("travel", "sightseeing"))
                .requiredParams(List.of("destination"))
                .timeoutMs(5000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> a = request.getArguments();
        String to = str(a.get("destination"), "目的地");
        String route = str(a.get("route"), to);

        String spots;
        if (route.contains("拉萨") || route.contains("西藏") || route.contains("川藏")) {
            spots = "康定折多山垭口、新都桥(摄影天堂)、理塘(高城)、然乌湖、米堆冰川、林芝鲁朗林海、布达拉宫";
        } else if (route.contains("青海") || route.contains("环湖")) {
            spots = "青海湖二郎剑、茶卡盐湖(天空之镜)、黑马河日出、祁连卓尔山、门源油菜花(7月)";
        } else if (route.contains("云南") || route.contains("大理") || route.contains("丽江")) {
            spots = "大理洱海生态廊道、双廊古镇、丽江古城、玉龙雪山、泸沽湖、香格里拉普达措";
        } else {
            spots = "建议沿主干道每 150-200km 选一个地标/古镇/观景台停留；优先选有补给的县城节点过夜";
        }

        String output = String.format(
                "沿途景点推荐（%s）：\n"
              + "· 打卡点：%s\n"
              + "· 停留建议：重点景点留 1.5-3h，观景台/垭口 20-40min；日落点提前 40min 到位\n"
              + "· 提示：高海拔景点放缓节奏、避免剧烈活动；热门点错峰(早 8 点前/午后)避人流",
                to, spots);
        return ToolExecuteResult.builder()
                .toolName(toolName()).success(true).output(output)
                .costMs(System.currentTimeMillis() - start).build();
    }

    private String str(Object v, String dft) {
        if (v == null) return dft;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? dft : s;
    }
}
