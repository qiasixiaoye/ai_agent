package com.vs.vsaiagent.workflowbuilder.service;

import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;

import java.util.List;
import java.util.Optional;

/**
 * Workflow 规划器抽象：自然语言需求 → Workflow IR。
 *
 * 设计为接口是为了让规则规划（{@link WorkflowPlanningService} 内置）与
 * LLM 规划（{@link LlmWorkflowPlanner}）可以解耦、可替换、可单测：
 * LLM 规划失败 / 不可用时返回 {@link Optional#empty()}，由上层回退到规则规划。
 */
public interface WorkflowPlanner {

    /**
     * 规划工作流。规划成功返回合法 IR；不可用 / 解析失败 / 校验不过时返回 empty。
     */
    Optional<WorkflowIR> plan(String requirement);

    /**
     * 带诊断收集的规划：失败原因（异常信息 / 校验错误）写入 {@code diagnostics}，供上层「错误分析审计」暴露给调用方，
     * 而不是像旧版那样只打日志后静默回退。默认实现委托无诊断版本（保证既有实现不破）。
     */
    default Optional<WorkflowIR> plan(String requirement, List<String> diagnostics) {
        return plan(requirement);
    }
}
