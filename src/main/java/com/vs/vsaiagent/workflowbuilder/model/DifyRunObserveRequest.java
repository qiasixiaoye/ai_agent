package com.vs.vsaiagent.workflowbuilder.model;

import java.util.Map;

/**
 * 触发「在 Dify 里运行并观测」请求。
 *
 * @param appId   已导入 Dify 的应用 id（来自导入结果）
 * @param appKind 应用形态：chatflow（advanced-chat，用 query 对话）/ workflow（用 inputs 表单）
 * @param query   运行输入（chatflow 的对话内容 / workflow 的输入文本）
 * @param inputs  workflow 形态的输入变量（可空，默认用 query 填 start.input）
 */
public record DifyRunObserveRequest(
        String appId,
        String appKind,
        String query,
        Map<String, Object> inputs
) {
}
