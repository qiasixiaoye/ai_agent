package com.vs.vsaiagent.skill.routing;

import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.SkillStep;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 无外部模型依赖的第一阶段 Skill Router。
 *
 * <p>对名称、标签、示例、描述、参数和步骤分别评分，只把超过阈值的 Top-K Skill
 * 交给 LLM。这样路由可复现、可解释，也避免随着 Skill 数量增长把全部 schema 塞入上下文。</p>
 */
@Component
public class WeightedSkillRouter implements SkillRouter {

    private final SkillRegistry skillRegistry;
    private final int defaultTopK;
    private final double defaultThreshold;

    public WeightedSkillRouter(SkillRegistry skillRegistry,
                               @Value("${app.skill-routing.top-k:3}") int defaultTopK,
                               @Value("${app.skill-routing.threshold:0.24}") double defaultThreshold) {
        this.skillRegistry = skillRegistry;
        this.defaultTopK = clampTopK(defaultTopK);
        this.defaultThreshold = clampThreshold(defaultThreshold);
    }

    @Override
    public SkillRouteDecision route(String query) {
        return route(query, defaultTopK, defaultThreshold);
    }

    @Override
    public SkillRouteDecision route(String query, int topK, double threshold) {
        int useTopK = clampTopK(topK);
        double useThreshold = clampThreshold(threshold);
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return new SkillRouteDecision(query, useThreshold, useTopK, false, List.of(), List.of());
        }

        List<ScoredSkill> scored = skillRegistry.listAll().stream()
                .map(skill -> score(skill, normalizedQuery))
                .sorted(Comparator.comparingDouble(ScoredSkill::score).reversed()
                        .thenComparing(s -> s.skill().name()))
                .toList();

        Set<String> selected = new LinkedHashSet<>();
        for (ScoredSkill item : scored) {
            if (selected.size() >= useTopK || item.score() < useThreshold) break;
            selected.add(item.skill().name());
        }

        // 观测结果只保留最相关的若干项，避免注册表很大时日志膨胀。
        int evidenceLimit = Math.min(scored.size(), Math.max(useTopK, 5));
        List<SkillRouteCandidate> candidates = scored.subList(0, evidenceLimit).stream()
                .map(item -> new SkillRouteCandidate(
                        item.skill().name(),
                        item.skill().metadata().displayName(),
                        round(item.score()),
                        selected.contains(item.skill().name()),
                        item.reasons()))
                .toList();
        return new SkillRouteDecision(query, useThreshold, useTopK, !selected.isEmpty(),
                new ArrayList<>(selected), candidates);
    }

    private ScoredSkill score(Skill skill, String query) {
        SkillMetadata md = skill.metadata();
        double score = 0;
        List<String> reasons = new ArrayList<>();

        double identity = Math.max(containment(query, md.name()), containment(query, md.displayName()));
        if (identity > 0) {
            score += 0.68 * identity;
            reasons.add("name=" + round(identity));
        } else {
            double similarity = Math.max(dice(query, md.name()), dice(query, md.displayName()));
            if (similarity > 0.12) {
                score += 0.40 * similarity;
                reasons.add("nameSimilarity=" + round(similarity));
            }
        }

        double tag = maxContainment(query, md.tags());
        if (tag > 0) {
            score += 0.35 * tag;
            reasons.add("tag=" + round(tag));
        }

        double example = maxFieldScore(query, md.examples());
        if (example > 0.08) {
            score += 0.48 * example;
            reasons.add("example=" + round(example));
        }

        double description = fieldScore(query, md.description());
        if (description > 0.08) {
            score += 0.32 * description;
            reasons.add("description=" + round(description));
        }

        List<String> structure = new ArrayList<>();
        for (SkillParam p : md.inputs()) {
            structure.add(p.name());
            structure.add(p.description());
        }
        for (SkillStep step : md.steps()) {
            structure.add(step.name());
            structure.add(step.description());
        }
        double structureScore = maxFieldScore(query, structure);
        if (structureScore > 0.10) {
            score += 0.25 * structureScore;
            reasons.add("structure=" + round(structureScore));
        }
        return new ScoredSkill(skill, Math.min(1.0, score), reasons);
    }

    private static double maxContainment(String query, List<String> fields) {
        double max = 0;
        for (String field : fields) max = Math.max(max, containment(query, field));
        return max;
    }

    private static double maxFieldScore(String query, List<String> fields) {
        double max = 0;
        for (String field : fields) max = Math.max(max, fieldScore(query, field));
        return max;
    }

    private static double fieldScore(String query, String field) {
        double contains = containment(query, field);
        return contains > 0 ? contains : dice(query, field);
    }

    private static double containment(String query, String field) {
        String value = normalize(field);
        if (value.length() < 2) return 0;
        if (query.contains(value)) return 1;
        // 英文复合名按分隔符拆分，任一有意义词命中时也可召回。
        String raw = field == null ? "" : field.toLowerCase(Locale.ROOT);
        String[] words = raw.split("[^\\p{L}\\p{N}]+");
        int hits = 0;
        int meaningful = 0;
        for (String word : words) {
            String token = normalize(word);
            if (token.length() < 3) continue;
            meaningful++;
            if (query.contains(token)) hits++;
        }
        return meaningful == 0 ? 0 : (double) hits / meaningful;
    }

    /** 基于 Unicode 字符 bigram 的 Dice，相比按空格分词更适合中英文混合查询。 */
    private static double dice(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.length() < 2 || b.length() < 2) return a.equals(b) ? 1 : 0;
        List<String> aPairs = pairs(a);
        List<String> bPairs = new ArrayList<>(pairs(b));
        int intersection = 0;
        for (String pair : aPairs) {
            int index = bPairs.indexOf(pair);
            if (index >= 0) {
                intersection++;
                bPairs.remove(index);
            }
        }
        return (2.0 * intersection) / (aPairs.size() + pairs(b).size());
    }

    private static List<String> pairs(String text) {
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < text.length() - 1; i++) pairs.add(text.substring(i, i + 2));
        return pairs;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static int clampTopK(int value) {
        return Math.min(Math.max(value, 1), 10);
    }

    private static double clampThreshold(double value) {
        return Math.min(Math.max(value, 0), 1);
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private record ScoredSkill(Skill skill, double score, List<String> reasons) {
    }
}
