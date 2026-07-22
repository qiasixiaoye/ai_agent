package com.vs.vsaiagent.workflowbuilder.model;

/**
 * Workflow IR 节点。
 *
 * @param id          节点唯一 id（如 start / llm_task / answer）
 * @param type        节点类型：start | llm | tool | answer
 * @param title       节点展示标题
 * @param instruction llm 节点的指令；tool 节点的参数模板（JSON 字符串，
 *                    用 {@code ${start}} 占位符引用起始输入）；其他类型为 null
 * @param toolRef     tool 节点引用的能力，格式 {@code tool:<toolName>} 或
 *                    {@code skill:<skillName>}；其他类型为 null
 */
public record WorkflowNode(
        String id,
        String type,
        String title,
        String instruction,
        String toolRef
) {
    public static final String TYPE_START = "start";
    public static final String TYPE_LLM = "llm";
    public static final String TYPE_TOOL = "tool";
    public static final String TYPE_ANSWER = "answer";

    public static WorkflowNode start() {
        return new WorkflowNode("start", TYPE_START, "开始", null, null);
    }

    public static WorkflowNode llm(String id, String title, String instruction) {
        return new WorkflowNode(id, TYPE_LLM, title, instruction, null);
    }

    public static WorkflowNode tool(String id, String title, String toolRef, String argsTemplate) {
        return new WorkflowNode(id, TYPE_TOOL, title, argsTemplate, toolRef);
    }

    public static WorkflowNode answer() {
        return new WorkflowNode("answer", TYPE_ANSWER, "直接回复", null, null);
    }
}
