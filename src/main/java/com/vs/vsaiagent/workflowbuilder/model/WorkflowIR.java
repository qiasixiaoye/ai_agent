package com.vs.vsaiagent.workflowbuilder.model;

import java.util.List;

/**
 * Workflow 中间表示（Intermediate Representation）。
 *
 * 设计约束（MVP）：
 *  - 由 WorkflowPlanningService 规则生成，不直接来自大模型输出
 *  - DSL 生成必须走 IR → Java 模板 → YAML，禁止 LLM 直接写 YAML
 *
 * @param id          工作流 id（UUID）
 * @param name        工作流名称
 * @param description 描述（一般为用户原始需求）
 * @param nodes       节点列表
 * @param edges       边列表
 */
public record WorkflowIR(
        String id,
        String name,
        String description,
        List<WorkflowNode> nodes,
        List<WorkflowEdge> edges
) {
}
