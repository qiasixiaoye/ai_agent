package com.vs.vsaiagent.workflowbuilder.service;

import com.vs.vsaiagent.workflowbuilder.model.WorkflowEdge;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 自然语言需求 → Workflow IR（MVP：规则生成，不调用大模型）。
 *
 * 固定生成 Start → LLM → Answer 三节点串行流程，
 * 用户 requirement 整体作为 LLM 节点 instruction。
 *
 * 二期：升级为 Spring AI Planner（LLM 输出 JSON IR，规则生成作为兜底）。
 */
@Service
public class WorkflowPlanningService {

    static final String LLM_NODE_ID = "llm_task";
    private static final int NAME_MAX_LENGTH = 20;

    public WorkflowIR plan(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("requirement 不能为空");
        }
        String trimmed = requirement.trim();
        String name = generateName(trimmed);

        WorkflowNode start = WorkflowNode.start();
        WorkflowNode llm = WorkflowNode.llm(LLM_NODE_ID, name, buildInstruction(trimmed));
        WorkflowNode answer = WorkflowNode.answer();

        List<WorkflowEdge> edges = List.of(
                new WorkflowEdge(start.id(), llm.id()),
                new WorkflowEdge(llm.id(), answer.id())
        );
        return new WorkflowIR(UUID.randomUUID().toString(), name, trimmed, List.of(start, llm, answer), edges);
    }

    /**
     * 规则命名：按关键词识别三类内置任务，否则截断需求作为名称。
     */
    private String generateName(String requirement) {
        if (containsAny(requirement, "总结", "摘要", "概括", "要点")
                && !containsAny(requirement, "提取", "抽取")) {
            return "文本总结工作流";
        }
        if (containsAny(requirement, "提取", "抽取", "解析出")) {
            return "信息提取工作流";
        }
        if (containsAny(requirement, "问答", "知识库", "答疑")) {
            return "文档问答工作流";
        }
        String head = requirement.length() > NAME_MAX_LENGTH
                ? requirement.substring(0, NAME_MAX_LENGTH)
                : requirement;
        return head + "工作流";
    }

    /**
     * LLM 节点 instruction：需求原文 + 通用输出约束。
     */
    private String buildInstruction(String requirement) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个任务执行助手。请严格按照下面的任务要求处理用户输入。\n\n");
        sb.append("任务要求：").append(requirement).append("\n\n");
        if (containsAny(requirement, "JSON", "json")) {
            sb.append("输出要求：只输出合法 JSON，不要包含 markdown 代码块标记或多余解释。");
        } else {
            sb.append("输出要求：结构清晰、关键结论先行，不要编造内容。");
        }
        return sb.toString();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
