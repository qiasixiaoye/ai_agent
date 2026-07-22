# Dify Agent 的 MCP 工具调用 ↔ 后端工具执行 trace 关联（设计稿）

> 状态：**L1 + L2 均已实施并验证（2026-06-17）**。对应 `ROADMAP.md:173`、`docs/PROJECT_GUIDE.md:478`
> 已登记的「MCP 透传 trace_id」缺口（现为「已部分实现：A+C 启发式关联」）。
> 决策：**L1 先做；L2 采用 A+C（时间窗 + 工具名 启发式，MCP sessionId 二级过滤）**。

## 实施记录
### L1（已完成 2026-06-17）
- 新增 `observability/tool/McpToolLoggingCallback`：MCP 出口每次工具调用自起一条 `mcp-tool-call` request
  （INPUT/TOOL/OUTPUT stage，TOOL stage 带 `tool_name`+`cost_ms`），观测写入全程吞异常。
- `config/McpCapabilityServerConfig` 给每个 capability callback 套上该装饰器。
- **E2E 验证**：agent 形态生成→导入→`dify-run`，Dify Agent 经 MCP 调了
  `astrophoto_settings`/`exposure_advisor`/`milkyway_rise`/`light_pollution`/`cloud_cover`/`astro_plan_summary`，
  全部落成 `mcp-tool-call` 审计行，TOOL stage 带工具名与耗时——L2 join 所需键已具备。
- **已知软点（留给 L2）**：本次 `session_id` 落成 `default`，即 `currentMcpSessionId()` 取不到 MCP 传输层
  sessionId（该 SDK/线程上 `?sessionId=` 不可读）。⇒ §4.2 的 **C 二级过滤一期不可用**；L2 先靠时间窗主键，
  sessionId 捕获改到 L2 阶段再想办法（如握手时落 appId↔sessionId 映射，或换 SDK 版本）。

### L2（已完成 2026-06-17）
- `AgentRequestLogRepository.listBySceneBetween(scene, from, to, limit)`：按场景 + `started_at` 时间窗查询。
- `DifyRunObserveService.correlateMcpToolCalls(...)`：在 `parseAndRecord` 末尾，按 `[t0, now]`（各放宽
  `WINDOW_SLACK_MS=2000ms`）查窗口内 `mcp-tool-call` 行；若从 SSE 解析出 `CALL <tool>` 列表则按工具名过滤
  （`extractCalledTool` / `extractToolName` 两个纯函数，已单测），命中的关联进来；写一条 `OUTPUT`「关联MCP工具调用」
  stage 到本次运行 requestId 下，并返回。
- `DifyDraftRunResult` 新增 `List<CorrelatedToolCall> correlatedToolCalls`（requestId/toolName/success/costMs）。
- 前端 `WorkflowStudio.vue` run-trace 面板新增「🔗 关联后端 MCP 工具调用 N 次」区块，每个工具 chip 带耗时 + 审计 id。
- **E2E 验证**：一次 agent run 返回 `correlatedToolCalls` 6 条（astrophoto_settings/exposure_advisor/
  milkyway_rise/light_pollution/cloud_cover/astro_plan_summary，各带 costMs + 审计 id），且运行 requestId 下
  落了一条「关联MCP工具调用」stage：`启发式关联(时间窗+工具名) 6 次 MCP 工具调用：...`。
- **语义边界**：启发式关联，并发同名工具时间窗重叠仍可能误配；stage 文案已标注「启发式」。严格对账留给策略 B。
- 单测：`DifyRunObserveCorrelationTest`（5 个，测两个解析辅助函数，无需 DB/上下文）。

## 1. 背景与目标
Workflow Builder 的 **agent 形态**会生成 Dify Agent 节点，节点挂载本服务通过 MCP Server 暴露的工具
（`astrophoto_settings` / `coffee_recipe` 等）。一次 `POST /api/workflow-builder/dify-run` 会驱动 Dify
跑这个 app，Agent 在若干 ROUND 里自主调用这些 MCP 工具。

**目标**：把「某一次 dify-run（requestId=X）」与「这次运行期间 Dify Agent 实际打到后端的 MCP 工具调用」
在 observability 审计里关联起来，使得运行时工具级故障（填错参数、工具内部报错）可回溯到具体的运行。

## 2. 现状：断点有两层（已核对代码）

### L1 — 后端 MCP 工具调用根本没被追踪
- `config/McpCapabilityServerConfig.capabilityToolCallbackProvider` 暴露的是**裸**
  `AgentToolCallback` / `SkillCallbackAdapter`，**没有**包 `observability/tool/LoggingToolCallback`。
- `LoggingToolCallback` 只在 `tools/ToolRegistration`（内部 agent 链路，供 AssistantApp/VsManus）那条路套了。
- ⇒ Dify Agent 经 MCP 打过来的工具调用，在 observability **一条 stage 都不写**。

### L2 — 关联键缺失
- `POST /api/mcp/message` 走 `observability/config/TraceContextFilter`，但 Dify **不带**
  `X-Request-Id` / `X-Session-Id` ⇒ sessionId 恒为 `"default"`、requestId 是个**没落库**的随机 UUID。
- filter 只 `TraceContext.set(...)` 设 ThreadLocal，**不** `startRequest`，所以也没有 request 行。
- Dify 的 MCP client **不转发自定义 HTTP header**，所以无法在请求头里塞 dify-run 的 requestId。

> 结论：必须 **先补 L1（让 MCP 工具调用可观测）→ 再解 L2（关联）**。L1 是纯增量、零外部依赖，
> 做完本身即有价值；L2 才是「较难」的部分。

## 3. L1 设计：让 MCP 工具调用先被记下来

### 3.1 在 capability provider 上套 logging 装饰器
改 `McpCapabilityServerConfig.capabilityToolCallbackProvider`：对每个 `ToolCallback` 包一层
logging 装饰器再返回。两个落地选项：

- **复用 `LoggingToolCallback`**：它已实现「`requestId != null` 时记 TOOL stage」。但它依赖
  `TraceContext.get().requestId()`，而 MCP 路径上没有有效 requestId（见 L2）。直接复用会因
  requestId 为随机未落库 UUID 而产生**孤儿 stage**。⇒ 需要先让 MCP 路径有一个**真正 startRequest 的 requestId**。
- **新增 `McpToolLoggingCallback`（推荐）**：专为 MCP server 出口设计，不依赖 ThreadLocal 既有 requestId，
  而是**每次工具调用自起一条 observability request**：
  - `scene = "mcp-tool-call"`
  - input = `{toolName, arguments, mcpSessionId}`
  - 调用 delegate → 记一条 TOOL stage（入参/出参/耗时/成败/error）
  - `success` / `fail` 收尾
  - 返回该工具调用自己的 requestId（后续 L2 关联就 join 这些行）

### 3.2 sessionId 取 MCP 传输层会话 id
MCP SSE 握手会分配 sessionId，POST `/mcp/message?sessionId=...` 会带上。把它作为这条
`mcp-tool-call` request 的 sessionId（`startRequest(scene, sessionId=mcpSessionId, ...)`）。
- 来源：从当前 HTTP 请求的 query param `sessionId` 读取（在装饰器里通过
  `RequestContextHolder` 或一个轻量 filter 暂存到 ThreadLocal）。
- 作用：L2 的二级过滤键（C 策略）。

### 3.3 L1 完成后的即时价值
即使 L2 还没接，后端审计里已能独立查到每次 MCP 工具调用的真实入参/报错——
排查「工具填错参数 / 工具内部异常」当下就够用。可按 `GET /observability/sessions/{mcpSessionId}/requests` 拉。

## 4. L2 设计：A+C 关联（时间窗 + 工具名，sessionId 二级过滤）

### 4.1 关联时机
在 `DifyRunObserveService.parseAndRecord(...)` 跑完、拿到 `[t0, t1]`（本次 dify-run 的起止）与
解析出的 Agent ROUND 列表后，追加一步**后处理关联**。

### 4.2 join 逻辑
1. 候选集：查 scene=`mcp-tool-call`、`started_at ∈ [t0, t1]` 的工具调用 request（L1 落的行）。
   - 数据访问：`AgentRequestLogRepository` 加一个 `listBySceneAndTimeRange(scene, t0, t1, limit)`（按时间窗 + scene）。
2. **二级过滤（C）**：若能确定本次 run 对应的 MCP sessionId，则只保留该 sessionId 的候选，收窄并发误配。
   - sessionId 获取：dify-run 暂时拿不到 Dify 那条 MCP 连接的 sessionId（不同进程）。
     一期可不强制；候选集本身已被 `[t0,t1]` 限定。二期再补 appId↔mcpSessionId 映射（见 §6）。
3. **工具名对齐**：把候选工具调用按 `tool_name` 与 SSE 里解析的 ROUND/工具序列对齐。
4. 产出：
   - 给本次 dify-run 的 requestId 写一条 `OUTPUT` 关联 stage：`关联 MCP 工具调用 N 条：[requestId...]`；
   - `DifyDraftRunResult` 增字段 `List<String> correlatedToolRequestIds`（前端 run-trace 面板可展开）。

### 4.3 明确语义边界
A+C 是**关联（correlation）而非因果证明**：并发跑多个 app、同名工具时间窗重叠仍可能误配。
关联 stage 文案需标注「启发式关联（时间窗+工具名）」，避免被当成精确对账。
需要严格对账时再走策略 B（参数注入令牌），作为可选高保真开关，不进主路径。

## 5. 改动点清单（实施时）
- `config/McpCapabilityServerConfig`：capability callbacks 包 `McpToolLoggingCallback`。
- 新增 `observability/tool/McpToolLoggingCallback`（或扩展 `LoggingToolCallback`）：自起 `mcp-tool-call` request。
- 新增轻量手段把 MCP `sessionId`（query param）暴露给装饰器（filter 暂存 ThreadLocal，或 RequestContextHolder）。
- `observability/repository/AgentRequestLogRepository`：+`listBySceneAndTimeRange(scene, from, to, limit)`。
- `workflowbuilder/service/DifyRunObserveService`：`parseAndRecord` 末尾加关联 join + 写关联 stage。
- `dify/dto/DifyDraftRunResult`：+`List<String> correlatedToolRequestIds`。
- 前端 `WorkflowStudio.vue` run-trace 面板：展示关联到的 MCP 工具调用 requestId（可点开看入参/报错）。
- 文档：`ROADMAP.md` / `PROJECT_GUIDE.md` 对应缺口状态更新为「已部分实现（A+C）」。

## 6. 二期可选增强（本次不做）
- **appId ↔ mcpSessionId 映射**：在 MCP SSE 握手或首次工具调用时落一张映射表，让 §4.2 的 C 过滤
  能精确到「哪个 Dify app」，进一步收窄误配。
- **策略 B（参数注入令牌）开关**：生成 Agent 指令时塞 run-token，要求每次工具调用带
  `__trace=<token>`；后端按 token 精确关联。仅在需严格对账时开启。
- 升级 MCP SDK / 协议层 metadata 透传（M6→正式版），若未来支持自定义 header 则 L2 可换成精确 header 关联。

## 7. 验证（实施后）
1. L1：用 agent 形态生成→导入→`dify-run`，跑完后 `GET /observability/sessions/{mcpSessionId}/requests`
   能看到若干 `mcp-tool-call` request，每条含真实工具入参/出参/耗时。
2. L2：dify-run 返回的 `correlatedToolRequestIds` 非空且工具名与 SSE ROUND 一致；
   审计里 dify-run 的 requestId 下能看到「关联 MCP 工具调用 N 条」stage。
3. 误配边界：并发跑两个含同名工具的 app，确认关联 stage 标注了「启发式」且未声称精确。
