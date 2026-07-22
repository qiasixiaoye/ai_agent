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
 * 摄影曝光建议：根据拍摄场景给出 ISO / 光圈 / 快门起手参数（曝光三角）。
 * 纯规则，无外部依赖，演示稳定。
 */
@Component
public class ExposureAdvisorAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("exposure_advisor")
                .displayName("曝光参数建议")
                .description("根据拍摄场景（日出日落/室内/运动/夜景/风光/人像等）推荐 ISO、光圈、快门起手参数")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("photography", "exposure"))
                .requiredParams(List.of("scene"))
                .timeoutMs(5000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        String scene = String.valueOf(request.getArguments().getOrDefault("scene", ""));
        String s = scene.toLowerCase();

        String iso, aperture, shutter, tip;
        if (contains(s, "日落", "日出", "黄金", "sunset", "sunrise", "golden")) {
            iso = "100"; aperture = "f/8"; shutter = "1/125"; tip = "用渐变灰滤镜压天空；包围曝光保高光细节。";
        } else if (contains(s, "夜", "星", "银河", "night", "astro")) {
            iso = "3200"; aperture = "f/2.8"; shutter = "15-25s"; tip = "三脚架 + 快门线，对焦无穷远，RAW。";
        } else if (contains(s, "运动", "体育", "宠物", "鸟", "sport", "action")) {
            iso = "400-800"; aperture = "f/4"; shutter = "1/1000+"; tip = "连拍 + 伺服对焦冻结动作。";
        } else if (contains(s, "室内", "indoor", "咖啡馆", "餐厅")) {
            iso = "1600"; aperture = "f/2.0"; shutter = "1/60"; tip = "大光圈进光，必要时开防抖。";
        } else if (contains(s, "人像", "portrait")) {
            iso = "200"; aperture = "f/1.8"; shutter = "1/250"; tip = "大光圈虚化背景，对焦眼睛。";
        } else if (contains(s, "风光", "风景", "landscape")) {
            iso = "100"; aperture = "f/11"; shutter = "1/125"; tip = "小光圈大景深，三脚架求锐利。";
        } else {
            iso = "200"; aperture = "f/5.6"; shutter = "1/250"; tip = "通用安全参数，按现场光线微调。";
        }

        String output = String.format(
                "场景「%s」建议起手参数：ISO %s ｜ 光圈 %s ｜ 快门 %s。\n提示：%s",
                scene.isBlank() ? "通用" : scene, iso, aperture, shutter, tip);
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
}
