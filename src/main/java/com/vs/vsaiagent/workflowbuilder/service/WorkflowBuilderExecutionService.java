package com.vs.vsaiagent.workflowbuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.registry.ToolRegistry;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillResult;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowRunResult;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowStepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workflow IR 本地试运行：不依赖 Dify，直接在本服务内按节点顺序执行，
 * 用于在前端以 pipeline 形式展示每一步结果（风格参考
 * {@link com.vs.vsaiagent.workflow.service.WorkflowExecutor}）。
 *
 * 变量替换：{@code ${start}}、{@code ${<nodeId>}}，引用前序节点的输出。
 */
@Slf4j
@Service
public class WorkflowBuilderExecutionService {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;

    public WorkflowBuilderExecutionService(ChatModel chatModel, ToolRegistry toolRegistry,
                                            SkillRegistry skillRegistry, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
        this.objectMapper = objectMapper;
    }

    public WorkflowRunResult run(WorkflowIR ir, String input) {
        if (ir == null || ir.nodes() == null || ir.nodes().isEmpty()) {
            throw new IllegalArgumentException("WorkflowIR 不能为空");
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("start", input == null ? "" : input);

        List<WorkflowStepResult> steps = new ArrayList<>();
        boolean allSuccess = true;
        String lastOutput = "";

        for (WorkflowNode node : ir.nodes()) {
            WorkflowStepResult step = runNode(node, variables, lastOutput);
            steps.add(step);
            if (step.success()) {
                lastOutput = step.output() == null ? "" : step.output();
                variables.put(node.id(), lastOutput);
            } else {
                allSuccess = false;
                break;
            }
        }

        return new WorkflowRunResult(steps, allSuccess, allSuccess ? lastOutput : "");
    }

    private WorkflowStepResult runNode(WorkflowNode node, Map<String, String> variables, String previousOutput) {
        long t = System.currentTimeMillis();
        try {
            switch (node.type()) {
                case WorkflowNode.TYPE_START -> {
                    return new WorkflowStepResult(node.id(), node.type(), node.title(), true,
                            variables.get("start"), null, System.currentTimeMillis() - t);
                }
                case WorkflowNode.TYPE_TOOL -> {
                    return runToolNode(node, variables, t);
                }
                case WorkflowNode.TYPE_LLM -> {
                    String prompt = render(node.instruction(), variables);
                    String out = chatClient.prompt().user(prompt).call().content();
                    return new WorkflowStepResult(node.id(), node.type(), node.title(), true,
                            out, null, System.currentTimeMillis() - t);
                }
                case WorkflowNode.TYPE_ANSWER -> {
                    return new WorkflowStepResult(node.id(), node.type(), node.title(), true,
                            previousOutput, null, System.currentTimeMillis() - t);
                }
                default -> {
                    return new WorkflowStepResult(node.id(), node.type(), node.title(), false,
                            null, "不支持的节点类型: " + node.type(), System.currentTimeMillis() - t);
                }
            }
        } catch (Exception e) {
            log.warn("[workflow-builder-run] node {} failed", node.id(), e);
            return new WorkflowStepResult(node.id(), node.type(), node.title(), false,
                    null, e.getMessage(), System.currentTimeMillis() - t);
        }
    }

    private WorkflowStepResult runToolNode(WorkflowNode node, Map<String, String> variables, long t) throws Exception {
        String toolRef = node.toolRef() == null ? "" : node.toolRef();
        Map<String, Object> args = buildArgs(node.instruction(), variables);

        if (toolRef.startsWith("tool:")) {
            String name = toolRef.substring("tool:".length());
            ToolExecuteRequest request = ToolExecuteRequest.builder()
                    .toolName(name)
                    .arguments(args)
                    .build();
            ToolExecuteResult result = toolRegistry.findByName(name)
                    .orElseThrow(() -> new IllegalArgumentException("工具未注册: " + name))
                    .execute(request);
            return new WorkflowStepResult(node.id(), node.type(), node.title(), result.isSuccess(),
                    result.getOutput(), result.isSuccess() ? null : result.getErrorMessage(),
                    System.currentTimeMillis() - t);
        } else if (toolRef.startsWith("skill:")) {
            String name = toolRef.substring("skill:".length());
            SkillResult result = skillRegistry.find(name)
                    .orElseThrow(() -> new IllegalArgumentException("技能未注册: " + name))
                    .execute(args, SkillContext.empty());
            return new WorkflowStepResult(node.id(), node.type(), node.title(), result.success(),
                    result.success() ? String.valueOf(result.data()) : null,
                    result.success() ? null : result.errorMessage(),
                    System.currentTimeMillis() - t);
        } else {
            throw new IllegalArgumentException("不支持的 toolRef: " + toolRef);
        }
    }

    /**
     * 构造 tool 节点参数：参数模板（如 {@code {"query": "${start}"}}）本身是合法 JSON，
     * 先解析为 Map，再对各 String 值做变量替换。这样用户输入永远不会进入 JSON 解析，
     * 避免输入中的引号 / 反斜杠 / 换行破坏 JSON 结构导致整步失败。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildArgs(String argsTemplate, Map<String, String> variables) throws Exception {
        if (argsTemplate == null || argsTemplate.isBlank()) {
            return new HashMap<>();
        }
        Map<String, Object> template = objectMapper.readValue(argsTemplate, Map.class);
        Map<String, Object> args = new HashMap<>();
        for (Map.Entry<String, Object> e : template.entrySet()) {
            Object v = e.getValue();
            args.put(e.getKey(), v instanceof String s ? render(s, variables) : v);
        }
        return args;
    }

    private String render(String tpl, Map<String, String> vars) {
        if (tpl == null) {
            return "";
        }
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
