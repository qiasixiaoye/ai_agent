package com.vs.vsaiagent.skill.builtin;

import com.vs.vsaiagent.skill.AbstractSkill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.SkillSourceType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 摩旅装备清单：按季节与天数生成护具/衣物/工具/电子/证件打包清单。纯规则，演示稳定。
 */
@Component
public class MotoGearChecklistSkill extends AbstractSkill {

    public static final String SKILL_NAME = "moto-gear-checklist";

    @Override
    protected SkillMetadata defaultMetadata() {
        return SkillMetadata.builder()
                .name(SKILL_NAME)
                .displayName("摩旅装备清单")
                .description("按季节与天数生成摩托旅行的护具、衣物、工具、电子与证件打包清单")
                .version("1.0.0")
                .tags(List.of("motorcycle", "travel", "checklist", "摩旅", "摩托旅行", "装备", "打包清单", "雨季骑行"))
                .inputs(List.of(
                        SkillParam.required("season", "string", "季节：春/夏/秋/冬 或 雨季"),
                        SkillParam.optional("days", "number", "天数，默认 3", "3")
                ))
                .outputs(List.of(
                        SkillParam.optional("checklist", "string", "打包清单", null)
                ))
                .examples(List.of("夏季 5 天摩旅的装备清单"))
                .timeoutMs(5000L)
                .sourceType(SkillSourceType.LOCAL)
                .build();
    }

    @Override
    protected Object doExecute(Map<String, Object> arguments, SkillContext context) {
        String season = String.valueOf(arguments.getOrDefault("season", "通用"));
        int days = (int) toDouble(arguments.get("days"), 3);

        List<String> seasonal = new ArrayList<>();
        if (season.contains("冬")) {
            seasonal.add("加热手套 / 把套、保暖分层、防风面罩、电加热马甲");
        } else if (season.contains("夏")) {
            seasonal.add("透气网眼骑行服、防晒冰袖、备用速干内衣、补水盐丸");
        } else if (season.contains("雨")) {
            seasonal.add("两件套雨衣、防水手套、链条防锈油、护目镜防雾剂");
        } else {
            seasonal.add("可拆卸内胆骑行服（早晚温差）、薄抓绒");
        }

        String checklist = String.format(
                "摩旅装备清单（%s · %d 天）\n"
              + "护具：头盔(备防雾)、护甲骑行服、手套、骑行靴、护膝护肘\n"
              + "季节专项：%s\n"
              + "工具：补胎胶条+打气、随车工具、保险丝、扎带胶带、备用拉线\n"
              + "电子：手机支架+车充、充电宝、行车记录仪/运动相机、对讲(车队)\n"
              + "证件随身：身份证、驾照、行驶证、保险单(电子+纸质)\n"
              + "衣物：按 %d 天准备速干衣%d套、洗漱、常用药、雨具",
                season, days, String.join("；", seasonal), days, Math.min(days, 4));
        return Map.of("checklist", checklist);
    }

    private double toDouble(Object v, double dft) {
        if (v == null) return dft;
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return dft; }
    }
}
