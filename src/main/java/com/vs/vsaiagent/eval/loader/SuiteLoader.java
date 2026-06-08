package com.vs.vsaiagent.eval.loader;

import com.vs.vsaiagent.eval.EvalCase;
import com.vs.vsaiagent.eval.EvalSuite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从 classpath:eval/suites/*.yaml 加载所有 EvalSuite。
 */
@Slf4j
@Component
public class SuiteLoader {

    private static final String SUITES_LOCATION = "classpath:eval/suites/*.yaml";

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public List<EvalSuite> loadAll() {
        List<EvalSuite> out = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources(SUITES_LOCATION);
            for (Resource r : resources) {
                EvalSuite s = parse(r);
                if (s != null) out.add(s);
            }
        } catch (Exception e) {
            log.warn("[suite-loader] scan failed: {}", e.getMessage());
        }
        log.info("[suite-loader] loaded {} suites", out.size());
        return out;
    }

    public Optional<EvalSuite> findByName(String name) {
        if (name == null) return Optional.empty();
        return loadAll().stream().filter(s -> name.equals(s.name())).findFirst();
    }

    @SuppressWarnings("unchecked")
    private EvalSuite parse(Resource r) {
        try (InputStream in = r.getInputStream()) {
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> root = new Yaml().loadAs(yaml, LinkedHashMap.class);
            if (root == null) return null;

            String name = str(root.get("name"));
            String desc = str(root.get("description"));
            String runner = str(root.get("runner"));
            String judge = str(root.get("judge"));

            List<EvalCase> cases = new ArrayList<>();
            Object casesNode = root.get("cases");
            if (casesNode instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) continue;
                    Map<String, Object> caseMap = (Map<String, Object>) m;
                    cases.add(new EvalCase(
                            str(caseMap.get("id")),
                            str(caseMap.get("input")),
                            toStringList(caseMap.get("expected_contains")),
                            str(caseMap.get("rubric")),
                            toStringList(caseMap.get("tags"))
                    ));
                }
            }
            return new EvalSuite(name, desc, runner, judge, cases);
        } catch (Exception e) {
            log.warn("[suite-loader] parse {} failed: {}", r.getFilename(), e.getMessage());
            return null;
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static List<String> toStringList(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object e : list) { if (e != null) out.add(e.toString()); }
        return out;
    }
}
