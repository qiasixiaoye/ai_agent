package com.vs.vsaiagent.workflowbuilder.model;

/**
 * 生成工作流请求。
 *
 * @param requirement 用户自然语言需求
 * @param mode        编排形态：{@code http}（默认，工具用 HTTP 请求节点固定编排）
 *                    或 {@code agent}（单个 Agent 节点挂载 MCP 工具，由 LLM 自主调用）
 * @param appKind     应用形态（Dify 图编排只有这两种 app.mode）：
 *                    {@code chatflow}（默认 → advanced-chat，多轮对话、用 answer 节点收尾、带会话记忆）
 *                    或 {@code workflow}（单轮、用 end 节点收尾、Start 带输入变量、跑一次出结果）。
 *                    与 {@code mode} 正交：http/agent × workflow/chatflow 共 4 种组合。
 */
public record WorkflowGenerateRequest(String requirement, String mode, String appKind) {

    public static final String MODE_HTTP = "http";
    public static final String MODE_AGENT = "agent";

    public static final String APP_WORKFLOW = "workflow";
    public static final String APP_CHATFLOW = "chatflow";

    public String modeOrDefault() {
        return mode == null || mode.isBlank() ? MODE_HTTP : mode.trim().toLowerCase();
    }

    /** 默认多轮对话（chatflow）：这是修复后语义自洽、且支持多轮的形态。 */
    public String appKindOrDefault() {
        return appKind == null || appKind.isBlank() ? APP_CHATFLOW : appKind.trim().toLowerCase();
    }
}
