# Unified Agent Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** 将普通对话、知识检索和 Agent 能力统一到一个自动编排入口，并以结构化事件驱动最终回答和安全记忆写入。

**Architecture:** 保留现有 `AssistantApp`、RAG、Tool、Skill、Manus 和分层记忆实现，在其上新增 `RequestOrchestrator`、能力路由、证据适配和事件协议。前端只消费结构化 SSE：执行事件进入折叠过程区，`final_delta` 才进入聊天消息，`final_completed` 才触发记忆候选。

**Tech Stack:** Spring Boot / Spring AI / Java records、SSE、Vue 3、Pinia、现有本地持久化与回归脚本。

## Global Constraints

- 不替换现有 RAG、MCP、Tool、Skill 和分层 Memory 的底层实现。
- 不新增外部数据库、向量库、消息队列或专用记忆模型服务。
- 高风险能力必须保留权限校验和用户确认；路由选择不等于授权。
- 原始 Tool 响应、Step 文本和流式中间片段不得进入 AI 聊天气泡或长期记忆。
- 继续支持本地会话恢复和现有 Tool/Skill/MCP 调用接口。
- 所有新增接口必须带 `conversationId`、`requestId`、`traceId`，并可从 Observability 追溯。

---

### Task 1: 建立统一事件与证据协议

**Files:**
- Create: `src/main/java/com/vs/vsaiagent/orchestration/OrchestrationEvent.java`
- Create: `src/main/java/com/vs/vsaiagent/orchestration/Evidence.java`
- Create: `src/main/java/com/vs/vsaiagent/orchestration/RoutePlan.java`
- Test: `src/test/java/com/vs/vsaiagent/orchestration/OrchestrationProtocolTest.java`

**Interfaces:**
- `OrchestrationEvent` 为不可变 record，字段为 `type`, `conversationId`, `requestId`, `traceId`, `payload`。
- `Evidence` 为不可变 record，字段为 `sourceType`, `sourceId`, `status`, `summary`, `provenance`, `payload`。
- `RoutePlan` 为不可变 record，`route` 取 `DIRECT|KNOWLEDGE|TOOL|SKILL|MIXED`，另带受限能力 ID 列表与 `requiresConfirmation`。

- [ ] **Step 1: Write the failing test**

  在 `OrchestrationProtocolTest` 中构造每个 record，断言 JSON 包含三个关联 ID、路由枚举拒绝未知值、`Evidence` 的 `payload` 不影响 `summary` 字段。

- [ ] **Step 2: Run test to verify it fails**

  Run: `mvnw.cmd -q -Dtest=OrchestrationProtocolTest test`

  Expected: FAIL because the orchestration records do not exist.

- [ ] **Step 3: Write minimal implementation**

  使用 Java record 和受限枚举实现协议；事件序列化交给项目已有 Jackson 配置，不在 record 中拼接字符串。

- [ ] **Step 4: Run test to verify it passes**

  Run: `mvnw.cmd -q -Dtest=OrchestrationProtocolTest test`

  Expected: PASS.

- [ ] **Step 5: Commit**

  `git add src/main/java/com/vs/vsaiagent/orchestration src/test/java/com/vs/vsaiagent/orchestration/OrchestrationProtocolTest.java && git commit -m "feat: add orchestration event protocol"`

### Task 2: 实现能力路由和策略门

**Files:**
- Create: `src/main/java/com/vs/vsaiagent/orchestration/CapabilityRouter.java`
- Create: `src/main/java/com/vs/vsaiagent/orchestration/CapabilityPolicy.java`
- Modify: `src/main/java/com/vs/vsaiagent/capability/governance/CapabilityGovernanceService.java`
- Test: `src/test/java/com/vs/vsaiagent/orchestration/CapabilityRouterTest.java`

**Interfaces:**
- `CapabilityRouter.route(String message, List<MemoryMatch> memories, CapabilityCatalog catalog)` 返回 `RoutePlan`。
- `CapabilityPolicy.check(RoutePlan plan, CallerContext context)` 返回 `PolicyDecision`，包括 `allowed`、`requiresConfirmation` 和原因。
- 复用治理服务现有风险、范围和状态字段，不改变现有 Tool/Skill/MCP 注册 API。

- [ ] **Step 1: Write the failing test**

  覆盖四个最小场景：普通闲聊返回 `DIRECT`；“根据项目资料”返回 `KNOWLEDGE`；“查今天的天气”返回 `TOOL`；包含“规划并执行”的复合请求返回 `MIXED`。另测高风险能力被策略门标记为需确认。

- [ ] **Step 2: Run test to verify it fails**

  Run: `mvnw.cmd -q -Dtest=CapabilityRouterTest test`

  Expected: FAIL because router and policy classes do not exist.

- [ ] **Step 3: Write minimal implementation**

  先实现确定性快速门：知识库意图、实时外部信息意图、执行性动词和高风险能力分别映射；无法唯一判断时返回 `MIXED` 并交给后续结构化模型计划。策略门在执行前检查 catalog 中的风险和权限。

- [ ] **Step 4: Run test to verify it passes**

  Run: `mvnw.cmd -q -Dtest=CapabilityRouterTest test`

  Expected: PASS.

- [ ] **Step 5: Commit**

  `git add src/main/java/com/vs/vsaiagent/orchestration src/main/java/com/vs/vsaiagent/capability/governance/CapabilityGovernanceService.java src/test/java/com/vs/vsaiagent/orchestration/CapabilityRouterTest.java && git commit -m "feat: route unified capability requests"`

### Task 3: 接入统一编排端点和结构化 SSE

**Files:**
- Create: `src/main/java/com/vs/vsaiagent/orchestration/RequestOrchestrator.java`
- Modify: `src/main/java/com/vs/vsaiagent/controller/AiController.java`
- Modify: `src/main/java/com/vs/vsaiagent/agent/ToolCallAgent.java`
- Modify: `src/main/java/com/vs/vsaiagent/agent/BaseAgent.java`
- Modify: `src/main/java/com/vs/vsaiagent/tools/WebSearchTool.java`
- Test: `src/test/java/com/vs/vsaiagent/orchestration/RequestOrchestratorTest.java`
- Test: `src/test/java/com/vs/vsaiagent/agent/VsManusTest.java`

**Interfaces:**
- 新增 `POST /api/ai/orchestrate/stream`，请求体 `{conversationId, message, confirmationToken?}`。
- 返回事件名为 `request_started`, `route_selected`, `retrieval_completed`, `capability_started`, `capability_completed`, `confirmation_required`, `final_delta`, `final_completed`, `request_failed`。
- 旧 `/ai/manus/chat` 保留兼容，但改为通过事件适配器输出；不得再把 `Step N` 写入最终回答。

- [ ] **Step 1: Write the failing test**

  使用 MockMvc 或现有 Spring 测试设施调用统一端点，断言事件顺序以 `request_started → route_selected → final_delta → final_completed` 结尾；包含 Tool 的请求断言 `capability_completed` 存在，但 `final_delta` 不包含 `工具:`、`Step 1:` 或完整 JSON。补充 WebSearch 空结果断言为 `empty` evidence。

- [ ] **Step 2: Run test to verify it fails**

  Run: `mvnw.cmd -q -Dtest=RequestOrchestratorTest,VsManusTest test`

  Expected: FAIL because current Manus path emits concatenated step text and no unified endpoint.

- [ ] **Step 3: Write minimal implementation**

  编排器按 `RoutePlan` 调用现有 `AssistantApp`/Manus/RAG 适配器；每个阶段发布 `ServerSentEvent<OrchestrationEvent>`。Tool 结果先转 `Evidence`，回答汇总器只接收 evidence。对空结果、异常、未确认高风险调用分别发状态事件并停止或降级。保留最终答案增量流。

- [ ] **Step 4: Run test to verify it passes**

  Run: `mvnw.cmd -q -Dtest=RequestOrchestratorTest,VsManusTest test`

  Expected: PASS, with no raw step leakage.

- [ ] **Step 5: Commit**

  `git add src/main/java/com/vs/vsaiagent/orchestration src/main/java/com/vs/vsaiagent/controller/AiController.java src/main/java/com/vs/vsaiagent/agent src/main/java/com/vs/vsaiagent/tools/WebSearchTool.java src/test/java/com/vs/vsaiagent/orchestration src/test/java/com/vs/vsaiagent/agent/VsManusTest.java && git commit -m "feat: expose unified orchestration stream"`

### Task 4: 将记忆改为完成事件驱动

**Files:**
- Create: `src/main/java/com/vs/vsaiagent/memory/MemoryCandidatePipeline.java`
- Create: `src/main/java/com/vs/vsaiagent/memory/MemoryCandidate.java`
- Modify: `src/main/java/com/vs/vsaiagent/memory/HierarchicalChatMemory.java`
- Modify: `src/main/java/com/vs/vsaiagent/memory/MemoryController.java`
- Modify: `vs-agent-web/src/stores/memory.js`
- Modify: `vs-agent-web/src/utils/streamLifecycle.js`
- Test: `src/test/java/com/vs/vsaiagent/memory/MemoryCandidatePipelineTest.java`
- Test: `vs-agent-web/scripts/workbench-regressions.test.mjs`

**Interfaces:**
- `MemoryCandidatePipeline.onTurnCompleted(CompletedTurn turn)` 返回结构化 `MemoryCandidate` 列表，并异步调用现有 semantic/episode 写入方法。
- `MemoryCandidate` 字段为 `type`, `content`, `importance`, `confidence`, `sensitivity`, `dedupeKey`, `status`。
- 前端只接收 `memory_candidate` 事件；删除当前把完整 AI 文本拼成字符串的 `suggestMemory` 路径。

- [ ] **Step 1: Write the failing test**

  测试稳定偏好生成候选、Tool 原文不生成候选、低置信敏感信息进入 `pending`、重复 `dedupeKey` 合并、流中断不触发写入。前端回归测试断言事件完成后显示状态而非 raw textarea。

- [ ] **Step 2: Run test to verify it fails**

  Run: `mvnw.cmd -q -Dtest=MemoryCandidatePipelineTest test; node vs-agent-web/scripts/workbench-regressions.test.mjs`

  Expected: FAIL because the current pipeline is frontend-only and consumes raw response text.

- [ ] **Step 3: Write minimal implementation**

  仅在 `final_completed` 后构造候选；采用规则优先的提取器，不增加每轮 LLM 调用。默认自动写入非敏感且高置信候选，其他候选保留确认状态。保留现有分层记忆的去重、摘要和检索能力。

- [ ] **Step 4: Run test to verify it passes**

  Run: `mvnw.cmd -q -Dtest=MemoryCandidatePipelineTest test; node vs-agent-web/scripts/workbench-regressions.test.mjs`

  Expected: PASS.

- [ ] **Step 5: Commit**

  `git add src/main/java/com/vs/vsaiagent/memory vs-agent-web/src/stores/memory.js vs-agent-web/src/utils/streamLifecycle.js src/test/java/com/vs/vsaiagent/memory/MemoryCandidatePipelineTest.java vs-agent-web/scripts/workbench-regressions.test.mjs && git commit -m "feat: trigger memory candidates from completed turns"`

### Task 5: 统一聊天页展示并保留管理入口

**Files:**
- Modify: `vs-agent-web/src/views/workbench/ChatWorkspace.vue`
- Modify: `vs-agent-web/src/components/workbench/InspectorPanel.vue`
- Modify: `vs-agent-web/src/stores/chat.js`
- Modify: `vs-agent-web/src/services/api.js`
- Test: `vs-agent-web/scripts/workbench-regressions.test.mjs`

**Interfaces:**
- `ChatWorkspace` 通过统一 SSE 事件渲染最终回答和 `executionSummary`。
- `InspectorPanel` 只渲染执行摘要、记忆状态与跳转链接；完整 Trace/Memory/Context 继续由管理页承担。
- 保留创建、清空、重命名、分类、置顶、删除和 `localStorage` 会话恢复。

- [ ] **Step 1: Write the failing test**

  扩展回归脚本：模拟结构化事件流后，AI 消息只包含 `final_delta`；执行摘要能显示 Tool/RAG 状态；刷新后当前会话与消息仍在；清空会话不影响 Tool/Skill catalog。

- [ ] **Step 2: Run test to verify it fails**

  Run: `node vs-agent-web/scripts/workbench-regressions.test.mjs`

  Expected: FAIL because ChatWorkspace currently把整段流文本和原始 memory suggestion 作为 UI 状态。

- [ ] **Step 3: Write minimal implementation**

  在 stream parser 中按 event name 分流；仅把 `final_delta` 累加到 AI 气泡；将能力事件映射为简短中文状态；移除 UUID、raw candidate textarea、token budget 卡片，并保留“打开 Memory / Trace”入口。

- [ ] **Step 4: Run test to verify it passes**

  Run: `node vs-agent-web/scripts/workbench-regressions.test.mjs; npm.cmd --prefix vs-agent-web run validate:workbench; npm.cmd --prefix vs-agent-web run build`

  Expected: all PASS and frontend build succeeds.

- [ ] **Step 5: Commit**

  `git add vs-agent-web/src/views/workbench/ChatWorkspace.vue vs-agent-web/src/components/workbench/InspectorPanel.vue vs-agent-web/src/stores/chat.js vs-agent-web/src/services/api.js vs-agent-web/scripts/workbench-regressions.test.mjs && git commit -m "feat: unify chat execution presentation"`

### Task 6: Docker 同步与端到端验收

**Files:**
- Modify only generated frontend output in `vs-agent-web/dist/` during deployment; do not commit generated files unless the repository convention requires it.
- Optional docs update: `docs/AGENT_RUNTIME_COMPLETE_SUMMARY.md` if it exists and describes the old paths.

- [ ] **Step 1: Run backend regression**

  Run: `mvnw.cmd -q test`

  Expected: PASS.

- [ ] **Step 2: Build frontend**

  Run: `npm.cmd --prefix vs-agent-web run validate:workbench; npm.cmd --prefix vs-agent-web run build`

  Expected: PASS.

- [ ] **Step 3: Sync the running containers**

  Copy `vs-agent-web/dist` into `vs-agent-web:/usr/share/nginx/html` and reload nginx. If backend classes changed, rebuild or copy the backend artifact using the existing project deployment procedure; do not delete volumes or unrelated containers.

- [ ] **Step 4: Verify the user flow**

  Check: direct question; knowledge question; real-time Tool question with empty result; high-risk confirmation; refresh persistence; clear/new conversation; Memory candidate status; Trace link.

- [ ] **Step 5: Commit documentation and report evidence**

  Record test commands and container sync result in the final handoff. Do not claim completion without the command outputs and a browser smoke check.
