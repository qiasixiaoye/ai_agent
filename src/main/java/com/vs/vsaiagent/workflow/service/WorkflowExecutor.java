package com.vs.vsaiagent.workflow.service;

import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillResult;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import com.vs.vsaiagent.workflow.StepResult;
import com.vs.vsaiagent.workflow.WorkflowDef;
import com.vs.vsaiagent.workflow.WorkflowNode;
import com.vs.vsaiagent.workflow.WorkflowResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流执行器。按 nodes 顺序串行执行（MVP，未来可加 DAG 拓扑）。
 *
 * 变量替换：${input}、${varName}。所有 String 字段（prompt / skill args 值）都做替换。
 * 节点 outputVar 写入 variables map，可被后续节点引用。
 */
@Slf4j
@Service
public class WorkflowExecutor {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");

    private final ChatClient chatClient;
    private final SkillRegistry skillRegistry;

    public WorkflowExecutor(ChatModel chatModel, SkillRegistry skillRegistry) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.skillRegistry = skillRegistry;
    }

    public WorkflowResult execute(WorkflowDef def, String input) {
        long start = System.currentTimeMillis();
        Map<String, String> variables = new HashMap<>();
        variables.put("input", input == null ? "" : input);

        List<StepResult> steps = new ArrayList<>();
        boolean anyFail = false;
        String lastError = null;

        for (WorkflowNode node : def.nodes()) {
            StepResult sr = runNode(node, variables);
            steps.add(sr);
            if (sr.success()) {
                if (node.outputVar() != null && !node.outputVar().isBlank()) {
                    variables.put(node.outputVar(), sr.output() == null ? "" : sr.output());
                }
            } else {
                anyFail = true;
                lastError = sr.errorMessage();
                break; // 第一个失败即停
            }
        }

        String outputVar = def.outputVar();
        String finalOutput = outputVar != null ? variables.getOrDefault(outputVar, "") : "";
        long elapsed = System.currentTimeMillis() - start;

        return new WorkflowResult(
                def.id(),
                !anyFail,
                finalOutput,
                lastError,
                elapsed,
                steps
        );
    }

    private StepResult runNode(WorkflowNode node, Map<String, String> variables) {
        long t = System.currentTimeMillis();
        String renderedInput = null;
        try {
            if ("llm".equalsIgnoreCase(node.type())) {
                String prompt = render(node.prompt(), variables);
                renderedInput = prompt;
                String out = chatClient.prompt().user(prompt).call().content();
                return new StepResult(node.id(), node.type(), true, prompt, out, null,
                        System.currentTimeMillis() - t);
            } else if ("skill".equalsIgnoreCase(node.type())) {
                Skill skill = skillRegistry.find(node.skillName())
                        .orElseThrow(() -> new IllegalArgumentException("skill not found: " + node.skillName()));
                Map<String, Object> args = renderArgs(node.args(), variables);
                renderedInput = args.toString();
                SkillResult r = skill.execute(args, SkillContext.empty());
                String out = r.success()
                        ? String.valueOf(r.data())
                        : null;
                return new StepResult(node.id(), node.type(), r.success(), renderedInput, out,
                        r.success() ? null : r.errorMessage(), System.currentTimeMillis() - t);
            } else {
                return new StepResult(node.id(), node.type(), false, null, null,
                        "unsupported node type: " + node.type(), System.currentTimeMillis() - t);
            }
        } catch (Exception e) {
            log.warn("[workflow-exec] node {} failed", node.id(), e);
            return new StepResult(node.id(), node.type(), false, renderedInput, null,
                    e.getMessage(), System.currentTimeMillis() - t);
        }
    }

    private Map<String, Object> renderArgs(Map<String, Object> args, Map<String, String> vars) {
        if (args == null) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : args.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String s) {
                out.put(e.getKey(), render(s, vars));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    private String render(String tpl, Map<String, String> vars) {
        if (tpl == null) return null;
        Matcher m = VAR_PATTERN.matcher(tpl);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = vars.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
