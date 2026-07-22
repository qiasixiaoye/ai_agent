package com.vs.vsaiagent.skill.routing;

import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.SkillResult;
import com.vs.vsaiagent.skill.SkillSourceType;
import com.vs.vsaiagent.skill.registry.InMemorySkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedSkillRouterTest {

    private WeightedSkillRouter router;

    @BeforeEach
    void setUp() {
        InMemorySkillRegistry registry = new InMemorySkillRegistry();
        registry.register(skill("astro-shoot-plan", "银河拍摄计划",
                "根据经纬度、日期、银河升起时间、光污染和云量生成拍摄计划",
                List.of("astro", "photography"), List.of("北京今晚的银河摄影计划")));
        registry.register(skill("coffee-tasting-notes", "咖啡品鉴笔记",
                "根据咖啡豆产地和烘焙度分析风味并推荐冲煮方法",
                List.of("coffee", "tasting"), List.of("耶加雪菲浅烘风味笔记")));
        registry.register(skill("moto-gear-checklist", "摩旅装备清单",
                "根据季节和天数生成摩托旅行护具、衣物和工具清单",
                List.of("motorcycle", "travel", "checklist"), List.of("夏季五天摩旅装备")));
        registry.register(skill("pdf-generation", "PDF生成",
                "把给定文本写入PDF文档并返回文件路径",
                List.of("file", "document"), List.of("生成一份PDF文件")));
        router = new WeightedSkillRouter(registry, 3, 0.24);
    }

    @Test
    void routesCompositeAstroSkillAndKeepsEvidence() {
        SkillRouteDecision decision = router.route("帮我规划北京郊区今晚的银河摄影");

        assertTrue(decision.matched(), decision.toString());
        assertEquals("astro-shoot-plan", decision.selectedSkillNames().getFirst());
        assertTrue(decision.candidates().getFirst().score() >= decision.threshold());
        assertFalse(decision.candidates().getFirst().reasons().isEmpty());
    }

    @Test
    void routesDifferentDomainsWithoutCallingAnLlm() {
        assertRoute("耶加雪菲浅烘应该怎么冲", "coffee-tasting-notes");
        assertRoute("准备夏季五天摩旅要带哪些装备", "moto-gear-checklist");
        assertRoute("把这段内容生成PDF文件", "pdf-generation");
    }

    @Test
    void rejectsUnrelatedQueryInsteadOfForcingAMatch() {
        SkillRouteDecision decision = router.route("解释一下Java虚拟线程的调度原理");

        assertFalse(decision.matched());
        assertTrue(decision.selectedSkillNames().isEmpty());
    }

    @Test
    void honorsTopKAndThreshold() {
        SkillRouteDecision decision = router.route("生成旅行装备清单文档", 1, 0.20);

        assertTrue(decision.selectedSkillNames().size() <= 1);
        assertEquals(1, decision.requestedTopK());
        assertEquals(0.20, decision.threshold());
    }

    private Skill skill(String name, String displayName, String description,
                        List<String> tags, List<String> examples) {
        SkillMetadata metadata = SkillMetadata.builder()
                .name(name)
                .displayName(displayName)
                .description(description)
                .tags(tags)
                .examples(examples)
                .inputs(List.of(SkillParam.optional("input", "string", description, null)))
                .sourceType(SkillSourceType.LOCAL)
                .build();
        return new Skill() {
            @Override
            public SkillMetadata metadata() {
                return metadata;
            }

            @Override
            public SkillResult execute(Map<String, Object> arguments, SkillContext context) {
                return SkillResult.ok(Map.of("skill", name), 0);
            }
        };
    }

    private void assertRoute(String query, String expected) {
        SkillRouteDecision decision = router.route(query);
        assertTrue(decision.matched(), decision.toString());
        assertEquals(expected, decision.selectedSkillNames().getFirst(), decision.toString());
    }
}
