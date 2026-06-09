package com.vs.vsaiagent.workflow.runner;

import com.vs.vsaiagent.eval.EvalRunner;
import com.vs.vsaiagent.workflow.WorkflowDef;
import com.vs.vsaiagent.workflow.WorkflowResult;
import com.vs.vsaiagent.workflow.service.WorkflowExecutor;
import com.vs.vsaiagent.workflow.service.WorkflowRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 让 Eval 模块可以直接评测工作流。
 *
 * 单例 runner 自身没有 workflowId — 该实例对所有 workflow 共用。EvalService 调
 * runner.run() 之前会先用 setCurrent(workflowId) 切换执行目标。
 *
 * 简化做法：暴露 runFor(workflowId, input) 给上层 EvalService 使用；
 * 这里同时实现 EvalRunner.name() / run() 以兼容老接口（默认拿当前 thread-local 上下文）。
 */
@Slf4j
@Component
public class WorkflowEvalRunner implements EvalRunner {

    public static final String NAME = "workflow";

    private final WorkflowRegistry registry;
    private final WorkflowExecutor executor;
    private final ThreadLocal<String> currentWorkflowId = new ThreadLocal<>();

    @Autowired
    public WorkflowEvalRunner(WorkflowRegistry registry, @Lazy WorkflowExecutor executor) {
        this.registry = registry;
        this.executor = executor;
    }

    public void setCurrent(String workflowId) { currentWorkflowId.set(workflowId); }
    public void clearCurrent() { currentWorkflowId.remove(); }

    @Override
    public String name() { return NAME; }

    @Override
    public String run(String input, String chatId) {
        String wfId = currentWorkflowId.get();
        if (wfId == null) {
            throw new IllegalStateException("WorkflowEvalRunner: 未设置 currentWorkflowId");
        }
        WorkflowDef def = registry.find(wfId)
                .orElseThrow(() -> new IllegalArgumentException("workflow not found: " + wfId));
        WorkflowResult r = executor.execute(def, input);
        return r.output() == null ? (r.errorMessage() == null ? "" : "[error] " + r.errorMessage()) : r.output();
    }
}
