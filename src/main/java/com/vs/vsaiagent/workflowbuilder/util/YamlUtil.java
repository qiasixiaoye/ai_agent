package com.vs.vsaiagent.workflowbuilder.util;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.Map;

/**
 * SnakeYAML 封装：dump 统一块状风格；parse 使用 SafeConstructor 防止反序列化任意对象。
 */
public final class YamlUtil {

    private YamlUtil() {
    }

    public static String dump(Map<String, Object> root) {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);
        opts.setPrettyFlow(true);
        opts.setAllowUnicode(true);
        return new Yaml(opts).dump(root);
    }

    /**
     * 解析 YAML 为 Map。非法 YAML 抛 IllegalArgumentException；
     * 顶层不是 map（如纯字符串/列表）也视为非法。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String yamlText) {
        if (yamlText == null || yamlText.isBlank()) {
            throw new IllegalArgumentException("YAML 内容为空");
        }
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlText);
        } catch (Exception e) {
            throw new IllegalArgumentException("YAML 解析失败: " + e.getMessage(), e);
        }
        if (!(loaded instanceof Map)) {
            throw new IllegalArgumentException("YAML 顶层结构必须是对象(map)");
        }
        return (Map<String, Object>) loaded;
    }
}
