package com.vs.vsaiagent.skill.routing;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 加载 classpath 评测集并计算路由准确率、误命中和漏命中。 */
@Service
public class SkillRoutingEvalService {

    private static final String SUITE_PATH = "eval/skill-routing-cases.yaml";

    private final SkillRouter skillRouter;

    public SkillRoutingEvalService(SkillRouter skillRouter) {
        this.skillRouter = skillRouter;
    }

    @SuppressWarnings("unchecked")
    public SkillRoutingEvalReport evaluate() {
        try (InputStream input = new ClassPathResource(SUITE_PATH).getInputStream()) {
            Map<String, Object> suite = new Yaml().load(input);
            String suiteName = String.valueOf(suite.getOrDefault("name", "skill-routing"));
            List<Map<String, Object>> cases = (List<Map<String, Object>>) suite.getOrDefault("cases", List.of());
            List<SkillRoutingEvalReport.CaseResult> results = new ArrayList<>();
            int correct = 0;
            int falsePositives = 0;
            int falseNegatives = 0;
            for (Map<String, Object> item : cases) {
                String query = String.valueOf(item.getOrDefault("query", ""));
                Object expectedValue = item.get("expectedSkill");
                String expected = expectedValue == null ? null : String.valueOf(expectedValue);
                SkillRouteDecision decision = skillRouter.route(query);
                String actual = decision.selectedSkillNames().isEmpty()
                        ? null : decision.selectedSkillNames().getFirst();
                boolean passed = Objects.equals(expected, actual);
                if (passed) correct++;
                if (expected == null && actual != null) falsePositives++;
                if (expected != null && actual == null) falseNegatives++;
                double topScore = decision.candidates().isEmpty() ? 0 : decision.candidates().getFirst().score();
                results.add(new SkillRoutingEvalReport.CaseResult(query, expected, actual, passed, topScore));
            }
            double accuracy = cases.isEmpty() ? 0 : Math.round(correct * 10_000.0 / cases.size()) / 10_000.0;
            return new SkillRoutingEvalReport(suiteName, cases.size(), correct, accuracy,
                    falsePositives, falseNegatives, results);
        } catch (Exception e) {
            throw new IllegalStateException("load skill routing eval suite failed: " + SUITE_PATH, e);
        }
    }
}
