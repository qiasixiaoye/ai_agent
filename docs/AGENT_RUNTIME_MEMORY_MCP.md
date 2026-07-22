# Agent Runtime：MCP、分层记忆与上下文窗口

> 上下文预算、Skill 渐进式加载、工具结果压缩和可解释召回的最新实现见 [AGENT_RUNTIME_CONTEXT_ENGINEERING.md](./AGENT_RUNTIME_CONTEXT_ENGINEERING.md)。

## 1. 建设目标

本项目把 Skill 路由、MCP 工具、长期记忆和上下文窗口统一到一条可观测的 Agent Runtime 链路，而不是把它们做成彼此独立的功能页：

```text
用户请求
  -> Skill 路由（候选召回、评分、命中）
  -> Context Window（近期消息 + 摘要 + 语义记忆 + 情景记忆）
  -> LLM
  -> MCP Policy（白名单/黑名单/风险确认）
  -> Managed MCP（超时、熔断、脱敏、审计）
  -> 结果写入情景记忆
  -> 下一轮上下文按相关性召回
```

闭环成立的判断标准：工具执行结果不只返回给当前请求，还会成为当前会话的 episodic memory；后续请求经过 token budget 控制的上下文装配重新召回它，同时全链路留下 request/stage 日志。

## 2. 分层记忆模型

`HierarchicalChatMemory` 提供四层记忆：

| 层级 | 内容 | 写入方式 | 使用方式 |
| --- | --- | --- | --- |
| Working | 最近若干轮原始消息 | ChatMemory Advisor 自动写入 | 保留对话连续性 |
| Summary | 被工作记忆淘汰的历史摘要 | 超出 working limit 时自动压缩 | 保留较长会话主线 |
| Semantic | 用户偏好、事实、稳定约束 | 规则抽取或管理 API 显式写入 | 按当前问题相关性召回 |
| Episodic | 工具结果、阶段性事件、执行经验 | MCP 成功/失败后自动写入，也可扩展其他执行器 | 按当前问题相关性召回 |

每个会话保存为独立 JSON 文件。会话 ID 先做 SHA-256 映射，避免路径穿越；同一会话使用锁串行更新，通过临时文件和原子移动降低进程异常造成半文件的概率。

当前摘要采用确定性的滚动压缩，不额外消耗一次 LLM 调用。它的优势是便于本地演示和测试；生产环境可把摘要器替换为异步 LLM summarizer，并保留当前存储接口。

### 记忆管理接口

- `GET /api/memory/{conversationId}`：查看四层记忆快照。
- `GET /api/memory/{conversationId}/context?query=...&tokenBudget=...`：预览本轮上下文装配。
- `POST /api/memory/{conversationId}/semantic`：显式写入用户事实或偏好。
- `DELETE /api/memory/{conversationId}`：清除会话记忆。

## 3. 上下文窗口管理

`ContextWindowManager` 不把所有长期记忆无条件塞回 prompt，而是执行：

1. 读取滚动摘要；
2. 根据当前用户问题检索 semantic memory；
3. 检索 episodic memory；
4. 按优先级逐项加入，达到 `memory-token-budget` 后停止；
5. 输出实际估算 token、命中的层级和是否发生截断。

工作记忆仍由 Spring AI `MessageChatMemoryAdvisor` 注入；其余三层作为 system context 注入。这样近期原始对话与长期记忆的职责是分开的，也可以在日志中单独观察 `memory_context_assemble` 阶段。

这里采用轻量 token 估算而非特定模型 tokenizer，适合模型可切换的示例项目。生产部署若固定模型，应替换为对应 tokenizer 并把模型最大窗口、预留输出 token、工具 schema token 一起纳入预算。

## 4. MCP 工具治理

`ManagedMcpToolService` 是所有 MCP 调用的统一入口：

- 从 Spring AI 的 `ToolCallbackProvider` 发现工具并缓存目录；
- 支持 TTL 自动刷新和管理接口手动刷新；
- 对工具名执行 allow/deny/risk policy；
- 高风险工具不会自动暴露给 LLM，手动调用必须明确 `confirmed=true`；
- 每次执行有超时限制；连续失败达到阈值后打开熔断器，冷却后自动恢复；
- 输入日志对 token、password、api key 等常见敏感字段脱敏；
- 记录 request 与 `managed_mcp_tool_call` stage，成功或失败结果写入会话情景记忆。

### MCP 管理接口

- `GET /api/mcp-management/health`：provider、工具数、可用数和熔断数。
- `GET /api/mcp-management/tools`：工具目录、风险、启用状态和熔断状态。
- `POST /api/mcp-management/tools/refresh`：刷新工具目录。
- `POST /api/mcp-management/tools/invoke`：按工具名和 JSON 参数确定性调用。

MCP 官方协议把工具发现和调用定义为 `tools/list` 与 `tools/call`，并建议敏感操作保留人类确认。本项目通过 Spring AI provider 适配这些能力。由于当前依赖版本没有在本项目内暴露稳定的 `listChanged` 订阅抽象，当前以 TTL + 手动刷新实现目录一致性；这是明确的版本边界，不宣称已经实现服务端变更推送。

## 5. 配置

```yaml
app:
  memory:
    base-dir: ${user.dir}/tmp/agent-memory
    working-message-limit: 20
    summary-max-chars: 4000
    tier-item-limit: 100
  context:
    memory-token-budget: 2500
    semantic-top-k: 5
    episodic-top-k: 3
  mcp:
    catalog-ttl-ms: 30000
    tool-timeout-ms: 15000
    policy:
      allow:
      deny: terminal,shell,execute_command,run_command
      high-risk: delete,remove,write,send,publish,payment,deploy
    circuit:
      failure-threshold: 3
      reset-ms: 60000
```

`allow` 为空表示不启用额外白名单限制；一旦填写，只有匹配项可调用。规则当前按工具名包含匹配，适合作为演示闭环；多租户生产环境应继续加入用户/角色/租户维度的授权和持久化审批记录。

## 6. 前端演示路径

首页进入“Agent Runtime”：

1. 刷新 MCP 目录，观察工具风险和熔断状态；
2. 选择一个工具，填写 JSON 参数并确定性执行；
3. 输入同一个 `sessionId` 查看 episodic memory；
4. 使用相近问题预览 context，验证工具事件被重新召回；
5. 写入一条 semantic memory，再次预览命中结果与 token budget；
6. 在可观测页面按 requestId 查看请求和工具 stage。

## 7. 测试边界

自动化测试覆盖：

- 工作记忆淘汰、摘要生成、偏好抽取、落盘重载与路径安全；
- semantic/episodic 相关性召回与上下文预算；
- MCP 目录发现、策略拦截、结果写入情景记忆；
- 连续失败触发熔断；
- 原 Skill 路由命中与评测集回归。

当前文件型记忆适合单机演示，不支持多实例强一致；生产化应把 `MemoryStore` 抽象接到 PostgreSQL/Redis/向量库，并增加 embedding 检索、TTL/遗忘策略和数据加密。

## 8. 简历表述（以当前代码为准）

> 设计并实现统一 Agent Runtime：构建 working/summary/semantic/episodic 四层会话记忆与 token-budget 上下文装配；封装 MCP 工具发现、风险策略、人工确认、超时熔断、审计及执行结果回写，打通“工具调用—情景记忆—后续召回”的闭环，并以管理台和自动化测试完成可演示验证。

参考设计：

- MCP Tools specification: https://modelcontextprotocol.io/specification/2025-06-18/server/tools
- MCP architecture: https://modelcontextprotocol.io/docs/learn/architecture
- Claude Code memory: https://docs.anthropic.com/zh-CN/docs/claude-code/memory
- MemGPT: https://arxiv.org/abs/2310.08560
