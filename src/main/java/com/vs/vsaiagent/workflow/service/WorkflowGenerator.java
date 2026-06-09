package com.vs.vsaiagent.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import com.vs.vsaiagent.workflow.WorkflowDef;
import com.vs.vsaiagent.workflow.WorkflowEdge;
import com.vs.vsaiagent.workflow.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言 → WorkflowDef 生成器。
 *
 * 让 LLM 按给定 schema 输出 JSON。系统 prompt 携带：
 *  - 可用节点类型与字段说明
 *  - 当前 SkillRegistry 中所有 Skill 的元数据（确保生成的 skillName 是真实可用的）
 *  - 变量替换说明
 *
 * 鲁棒解析：剥离 markdown code fence；抽取第一段 {...} JSON。
 */
@Slf4j
@Service
public class WorkflowGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json|yaml)?\\s*([\\s\\S]*?)```", Pattern.MULTILINE);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*\\}", Pattern.MULTILINE);

    private final ChatClient chatClient;
    private final SkillRegistry skillRegistry;

    public WorkflowGenerator(ChatModel dashscopeChatModel, SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    public WorkflowDef generate(String userPrompt) {
        String systemMsg = buildSystemPrompt();
        String raw;
        try {
            raw = chatClient.prompt()
                    .system(systemMsg)
                    .user("用户需求：" + userPrompt + "\n\n只输出 JSON，不要 markdown 包裹，不要解释。")
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[workflow-gen] LLM call failed", e);
            throw new IllegalStateException("生成失败：" + e.getMessage());
        }
        log.info("[workflow-gen] raw output (truncated): {}",
                raw == null ? "null" : raw.substring(0, Math.min(200, raw.length())));
        JsonNode root = parseRobust(raw);
        if (root == null) {
            throw new IllegalStateException("LLM 输出不是合法 JSON，原文片段：" + truncate(raw, 200));
        }
        return toWorkflowDef(root, userPrompt);
    }

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是工作流编排专家。把用户需求转成一个 JSON 工作流定义。

                JSON 模板：
                {
                  "name": "短名字",
                  "description": "简短说明",
                  "nodes": [
                    { "id": "n1", "type": "llm",   "prompt": "...用 ${input}/${var} 引用变量...", "outputVar": "step1" },
                    { "id": "n2", "type": "skill", "skillName": "<已注册 skill>", "args": {"k": "${step1}"}, "outputVar": "step2" }
                  ],
                  "edges": [ { "from": "n1", "to": "n2" } ],
                  "outputVar": "step2"
                }

                规则：
                - 节点 type 只能是 "llm" 或 "skill"。
                - llm 节点必须有 prompt 字段；skill 节点必须有 skillName 与 args。
                - 变量替换：${input} 表示用户运行时输入；${varName} 表示前序节点 outputVar。
                - 节点数控制在 2-5 个，串行即可，edges 按 nodes 顺序连接。
                - skillName 必须从下方"可用 Skill 清单"中精确选取，名字不可杜撰。

                """);
        sb.append("可用 Skill 清单：\n");
        List<Skill> skills = skillRegistry.listAll();
        if (skills.isEmpty()) {
            sb.append("（无）— 此种情况只用 llm 节点。\n");
        } else {
            for (Skill s : skills) {
                SkillMetadata md = s.metadata();
                sb.append("- ").append(md.name()).append(" : ").append(safe(md.description()));
                if (!md.inputs().isEmpty()) {
                    sb.append(" | inputs: ");
                    for (SkillParam p : md.inputs()) {
                        sb.append(p.name()).append("(").append(p.type()).append(p.required() ? ", required" : "").append(") ");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private JsonNode parseRobust(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // 直接尝试
        JsonNode n = tryParse(s);
        if (n != null) return n;
        // code fence
        Matcher m = CODE_FENCE.matcher(s);
        if (m.find()) {
            n = tryParse(m.group(1).trim());
            if (n != null) return n;
        }
        // 抽第一段大括号
        Matcher m2 = JSON_OBJECT.matcher(s);
        if (m2.find()) {
            n = tryParse(m2.group());
            if (n != null) return n;
        }
        return null;
    }

    private JsonNode tryParse(String s) {
        try { return MAPPER.readTree(s); } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private WorkflowDef toWorkflowDef(JsonNode root, String userPrompt) {
        String id = UUID.randomUUID().toString();
        String name = textOr(root, "name", "未命名工作流");
        String desc = textOr(root, "description", userPrompt);

        List<WorkflowNode> nodes = new ArrayList<>();
        ArrayNode nodesNode = root.withArray("nodes");
        for (JsonNode n : nodesNode) {
            String nid = textOr(n, "id", "n" + (nodes.size() + 1));
            String type = textOr(n, "type", "llm");
            String prompt = n.path("prompt").isMissingNode() ? null : n.path("prompt").asText();
            String skillName = n.path("skillName").isMissingNode() ? null : n.path("skillName").asText();
            String outputVar = textOr(n, "outputVar", nid);
            Map<String, Object> args = new LinkedHashMap<>();
            JsonNode argsNode = n.path("args");
            if (argsNode.isObject()) {
                argsNode.fieldNames().forEachRemaining(k -> args.put(k, argsNode.get(k).isTextual() ? argsNode.get(k).asText() : argsNode.get(k)));
            }
            nodes.add(new WorkflowNode(nid, type, prompt, skillName, args, outputVar));
        }

        List<WorkflowEdge> edges = new ArrayList<>();
        for (JsonNode e : root.withArray("edges")) {
            edges.add(new WorkflowEdge(textOr(e, "from", ""), textOr(e, "to", "")));
        }
        String outputVar = textOr(root, "outputVar",
                nodes.isEmpty() ? null : nodes.get(nodes.size() - 1).outputVar());

        return new WorkflowDef(id, name, desc, nodes, edges, outputVar);
    }

    private static String textOr(JsonNode n, String field, String def) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? def : v.asText(def);
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String truncate(String s, int n) { return s == null ? "" : (s.length() > n ? s.substring(0, n) + "..." : s); }
}
