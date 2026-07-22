package com.vs.vsaiagent.skill.builtin;

import com.vs.vsaiagent.skill.AbstractSkill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.SkillSourceType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 咖啡品鉴：按产地与烘焙度给出风味轮廓、酸苦平衡与建议冲煮方式。纯规则，演示稳定。
 */
@Component
public class CoffeeTastingSkill extends AbstractSkill {

    public static final String SKILL_NAME = "coffee-tasting-notes";

    @Override
    protected SkillMetadata defaultMetadata() {
        return SkillMetadata.builder()
                .name(SKILL_NAME)
                .displayName("咖啡品鉴笔记")
                .description("按豆子产地与烘焙度生成风味轮廓、酸苦平衡与建议冲煮方式")
                .version("1.0.0")
                .tags(List.of("coffee", "tasting", "咖啡", "咖啡豆", "风味", "冲煮", "耶加雪菲", "哥伦比亚"))
                .inputs(List.of(
                        SkillParam.required("beans", "string", "豆子产地/名称，如 埃塞俄比亚耶加雪菲 / 哥伦比亚"),
                        SkillParam.optional("roast", "string", "烘焙度：浅/中/深，默认中", "中")
                ))
                .outputs(List.of(
                        SkillParam.optional("notes", "string", "风味品鉴笔记", null)
                ))
                .examples(List.of("耶加雪菲 浅烘 的风味笔记"))
                .timeoutMs(5000L)
                .sourceType(SkillSourceType.LOCAL)
                .build();
    }

    @Override
    protected Object doExecute(Map<String, Object> arguments, SkillContext context) {
        String beans = String.valueOf(arguments.getOrDefault("beans", "未知豆"));
        String roast = String.valueOf(arguments.getOrDefault("roast", "中"));
        String b = beans.toLowerCase();

        String origin;
        if (contains(b, "耶加", "埃塞", "yirga", "ethiop")) {
            origin = "花香、柑橘与莓果调，明亮高酸，茶感干净";
        } else if (contains(b, "哥伦比亚", "colombia")) {
            origin = "焦糖、坚果与红苹果，酸甜均衡，醇厚适中";
        } else if (contains(b, "曼特宁", "苏门答腊", "mandheling", "sumatra")) {
            origin = "草本、黑巧与木质，低酸厚体，醇厚绵长";
        } else if (contains(b, "巴西", "brazil")) {
            origin = "坚果、可可与奶油，低酸顺口，适合意式拼配";
        } else {
            origin = "均衡的甜感与适中酸度，干净的余韵";
        }

        String roastNote;
        if (roast.contains("浅")) {
            roastNote = "浅烘放大产地酸质与花果香，推荐手冲、92-94°C 凸显层次";
        } else if (roast.contains("深")) {
            roastNote = "深烘强调焦糖与苦甜醇厚，推荐意式/法压，奶咖表现佳";
        } else {
            roastNote = "中烘酸苦平衡、甜感饱满，手冲与意式皆宜";
        }

        String notes = String.format("【%s · %s烘】\n产地风味：%s。\n烘焙建议：%s。", beans, roast, origin, roastNote);
        return Map.of("notes", notes);
    }

    private boolean contains(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }
}
