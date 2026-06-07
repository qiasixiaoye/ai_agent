package com.vs.vsaiagent.skill.loader;

import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.SkillSourceType;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 SKILL.md 头部的 YAML front-matter 为 SkillMetadata。
 *
 * 期待格式：
 * <pre>
 * ---
 * name: pdf-generation
 * description: ...
 * inputs:
 *   - name: fileName
 *     type: string
 *     required: true
 * ---
 * # 正文 markdown
 * </pre>
 */
@Slf4j
public final class SkillMdParser {

    private static final String FRONT_MATTER_DELIMITER = "---";

    private SkillMdParser() {}

    public static SkillMetadata parse(InputStream in) {
        try {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse(content);
        } catch (Exception e) {
            log.warn("[skill-md] read failed", e);
            return null;
        }
    }

    public static SkillMetadata parse(String content) {
        if (content == null || content.isBlank()) return null;
        String trimmed = content.stripLeading();
        if (!trimmed.startsWith(FRONT_MATTER_DELIMITER)) {
            log.warn("[skill-md] missing front-matter delimiter, skip");
            return null;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0) return null;
        String rest = trimmed.substring(firstLineEnd + 1);
        int closing = rest.indexOf("\n" + FRONT_MATTER_DELIMITER);
        if (closing < 0) {
            log.warn("[skill-md] missing closing front-matter delimiter, skip");
            return null;
        }
        String yamlBlock = rest.substring(0, closing);
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.loadAs(yamlBlock, LinkedHashMap.class);
            if (root == null) return null;
            return toMetadata(root);
        } catch (Exception e) {
            log.warn("[skill-md] parse yaml failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static SkillMetadata toMetadata(Map<String, Object> root) {
        SkillMetadata.Builder b = SkillMetadata.builder()
                .name(str(root.get("name")))
                .displayName(str(root.get("displayName")))
                .description(str(root.get("description")))
                .version(str(root.get("version")))
                .tags(toStringList(root.get("tags")))
                .examples(toStringList(root.get("examples")))
                .inputs(toParams(root.get("inputs")))
                .outputs(toParams(root.get("outputs")));

        Object timeout = root.get("timeoutMs");
        if (timeout instanceof Number n) {
            b.timeoutMs(n.longValue());
        }
        Object srcType = root.get("sourceType");
        if (srcType != null) {
            try {
                b.sourceType(SkillSourceType.valueOf(srcType.toString().toUpperCase()));
            } catch (IllegalArgumentException ignore) {
                b.sourceType(SkillSourceType.LOCAL);
            }
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private static List<SkillParam> toParams(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<SkillParam> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> map = (Map<String, Object>) m;
            String name = str(map.get("name"));
            if (name == null || name.isBlank()) continue;
            result.add(new SkillParam(
                    name,
                    str(map.getOrDefault("type", "string")),
                    str(map.get("description")),
                    Boolean.TRUE.equals(map.get("required")),
                    map.get("defaultValue")
            ));
        }
        return result;
    }

    private static List<String> toStringList(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object e : list) {
            if (e != null) out.add(e.toString());
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
