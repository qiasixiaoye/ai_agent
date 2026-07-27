# Manus 真实流式与事件化 SSE 设计

## 目标

将 `GET /api/ai/manus/chat` 从“一个 ReAct step 完整结束后随机切分字符串”的伪流式，改为模型 token 到达即推送的真实流式。前端同时展示最终回答与可折叠的 Agent 执行过程。

## 范围

本次仅改造 Manus 的流式端点及其前端消费者；不改变工具选择、工具执行语义、会话持久化、RAG 或通用聊天 SSE。

## 协议

服务端保留 Spring MVC 的 `SseEmitter`，使用具名 SSE 事件并发送 JSON 对象：

| 事件 | 载荷 | 含义 |
| --- | --- | --- |
| `thinking` | `step`、`text` | 当前模型调用的文本增量 |
| `tool_call` | `step`、`name`、`arguments` | 模型已决定调用工具 |
| `tool_result` | `step`、`name`、`result` | 工具执行完毕 |
| `answer` | `step`、`text` | 无工具调用时的最终回答增量 |
| `complete` | `steps` | Agent 正常结束 |
| `error` | `message` | 不可恢复错误 |

`thinking` 只用于过程展示；`answer` 才累积为聊天记录中的最终助手消息。每次连接只发送一个终态事件：`complete` 或 `error`。

## 服务端流程

1. `ToolCallAgent` 增加可流式消费的思考入口，调用 `ChatClient.stream().chatResponse()`。
2. 每个 `ChatResponse` 到达时立即提取文本增量并通过回调发送 `thinking`；不再使用随机切分或 `Thread.sleep`。
3. 流完成后合并完整 `ChatResponse`，保留原有的工具调用解析与 `ToolCallingManager` 执行方式。
4. 有工具调用时，依序发出 `tool_call`、执行工具、发出 `tool_result`，再开始下一 ReAct step。
5. 无工具调用时，将同一次模型流的文本以 `answer` 事件发送，并结束 Agent。
6. 客户端断开、超时或模型异常时停止后续 step，发送或完成错误，并只清理一次运行状态。

## 前端流程

`AssistantApp.vue` 为 Manus 连接注册具名事件监听器：

- `answer` 增量更新最终助手消息；
- `thinking`、`tool_call`、`tool_result` 追加至单次请求的执行记录；
- `complete` 写入 Agent 历史并关闭连接；
- `error` 显示失败提示并关闭连接。

普通聊天和 RAG 保持其原有的默认 SSE 消息处理，不受影响。

## 错误处理与兼容性

这是 Manus 专用端点的协议升级，旧的默认纯文本 `onmessage` 消费不再适用于该端点。前后端在同一提交中同步升级。工具调用字段只在模型流完成、完整响应已收集后读取，避免依据不完整增量执行工具。

## 验证

- 为事件发射与 ReAct 流程写不依赖 Spring 上下文或数据库的单元测试；
- 先验证测试在旧实现上因缺少具名、按序事件而失败，再实现最小改动使其通过；
- 运行相关后端测试、前端构建，以及全量 Maven 测试；全量 Maven 测试当前已知会被既有 `VsManusTest` 的本地数据源缺失阻断，单独记录该环境问题。
