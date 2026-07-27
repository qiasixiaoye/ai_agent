# Agent 系统优化：知识点与关键代码

本文只记录仓库中已落地的机制；“后续方向”不等同于已经上线的能力。

## 1. 真流式响应与 SSE 事件契约

**知识点：** 流式的价值是降低首字等待，而不是把完整结果切片后伪装成打字机。前后端应按事件类型处理路由、能力、增量、记忆和失败。

```java
return prefix.concatWith(answer.map(text -> event("final_delta", conversationId, requestId, traceId,
        Map.of("text", append(finalAnswer, text)))));
```

`RequestOrchestrator` 统一发出 `route_selected`、`capability_started`、`final_delta`、`final_completed`。**兜底：** 任意流异常映射为 `request_failed`，前端不会将中断显示为成功。

## 2. 受限 Skill：固定编排而非模型猜测

**知识点：** Skill 是多步过程，必须声明内部步骤与工具边界。企业外勤演示不让模型任意选工具，而是固定白名单。

```java
private static final List<String> ALLOWED_TOOLS = List.of(
        "field_weather_summary", "public_transit_risk", "field_research_brief");

if (!ALLOWED_TOOLS.contains(toolName)) {
    throw new IllegalArgumentException("tool is outside enterprise field-research allow-list: " + toolName);
}
```

**兜底：** 出行数据不可用时以 `unavailable` 证据传递到汇总工具，禁止把“无数据”改写为“低风险”。

## 3. 工具证据与可追溯性

**知识点：** 工具结论必须绑定能力名、状态、来源、获取时间和降级理由，最终回答与 Trace 才能审计。

```java
public record FieldResearchEvidence(String capability, String status, String source,
        String observedAt, String summary, String fallbackReason) { }

events.add(event("evidence_recorded", conversationId, requestId, traceId,
        Map.of("evidence", evidence)));
```

前端将 `evidence_recorded` 写入工作台状态和消息详情。**兜底：** `unavailable` 与 `blocked` 均作为引用保留，避免只显示成功调用。

## 4. 不可逆操作的后端确认门禁

**知识点：** 前端按钮不是权限控制。导出审批单使用会话绑定、单次消费、限时令牌；只有服务端放行后才会调用导出工具。

```java
pending.compute(token, (ignored, confirmation) -> {
    if (confirmation != null && conversationId.equals(confirmation.conversationId())) {
        accepted.set(true);
        return null;
    }
    return confirmation;
});
```

**兜底：** 缺令牌、过期令牌或跨会话令牌均为 `blocked`；错误会话尝试不会消耗正确会话的令牌。

## 5. 长期记忆：完成后写入、敏感信息不自动保存

**知识点：** 记忆候选只能在回答完成后生成，避免把思考片段或工具原始输出写入长期存储。

```java
candidates = memoryCandidatePipeline.onTurnCompleted(
        new MemoryCandidatePipeline.CompletedTurn(conversationId, userMessage, finalAnswer, List.of()));
events.add(event("memory_candidate", conversationId, requestId, traceId,
        Map.of("candidate", candidate)));
```

**兜底：** 敏感字段进入待确认候选；写入失败不影响本轮回答，界面应明确显示失败而非伪造“已保存”。

## 6. 状态与并发边界

**知识点：** 运行时会话状态不能放在单例 Agent 可变字段。当前对话记忆以 conversationId 隔离并在持久化读写时加锁。

```java
ReentrantLock lock = lock(useId);
lock.lock();
try { return function.apply(state); }
finally { lock.unlock(); }
```

**后续方向：** 将所有 Agent 执行态继续收敛到显式 `ExecutionContext`，使配置 Bean 无状态。

## 7. 工具超时与故障隔离

**知识点：** 工具执行必须有超时边界，失败应结构化返回而非挂住 Agent 步骤。

```java
ToolExecuteResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
// TimeoutException -> success=false, errorMessage="工具执行超时"
```

**后续方向：** 对外部供应商增加熔断和批量异步日志；现有演示工具使用确定性本地数据，避免将演示包装为实时服务。

## 8. RAG、记忆与上下文的边界

**知识点：** RAG 是文档检索，记忆是用户或会话的长期信息，上下文诊断负责解释模型输入；三者的入口、引用和失败语义不能混用。

```java
case KNOWLEDGE -> assistantApp.doChatWithRagSse(message, conversationId);
case SKILL -> Mono.fromCallable(() -> assistantApp.doChatWithSkills(message, conversationId));
```

## 9. 可演示验收查询

在对话输入以下查询：

```text
我下周要去北京朝阳区做客户外勤调研。请结合天气和公共出行风险，
给出上午/下午建议；我偏好中文回答、先给结论再给步骤。
若需要导出含联系方式的行程审批单，请先向我确认。
```

应观察到：`SKILL` 路由、三项工具证据、待确认导出、确认后的演示审批编号、记忆候选和同一 Trace。天气/出行适配器故障时，应看到 `unavailable`，而非虚假的风险结论。
