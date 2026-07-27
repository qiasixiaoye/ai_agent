# 企业外勤调研演示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有聊天工作台中交付一条可验证的企业外勤调研链路，涵盖受限 Skill、工具证据、记忆引用、确认门禁、降级和 Trace。

**Architecture:** 后端用 `EnterpriseFieldResearchSkill` 作为白名单编排单元，工具治理服务继续作为所有 Tool 的唯一权限执行门。`RequestOrchestrator` 产生统一的能力、证据、记忆和阻断事件；Vue 聊天页把事件写入工作台状态，右侧检查器与最终消息分别展示运行证据和可引用信息。

**Tech Stack:** Java 21、Spring Boot、Spring AI、Reactor SSE、Vue 3、Pinia、Vite、JUnit 5、Node test runner、Docker Compose。

## Global Constraints

- 只读工具可自动执行；`IRREVERSIBLE_WRITE` 工具只能经后端确认令牌校验后执行。
- 演示数据必须标识为演示数据，不能表述成实时公共数据。
- RAG、长期记忆和上下文诊断保持独立模块与独立引用。
- 所有失败状态必须保留在 SSE/Trace 中，前端不得根据文案推断成功。
- 每项生产行为先写失败测试，再写最小实现；每个可验收任务单独提交并推送。

---

### Task 1: 定义企业外勤能力的事件与证据契约

**Files:**
- Create: `src/main/java/com/vs/vsaiagent/orchestration/FieldResearchEvidence.java`
- Modify: `src/main/java/com/vs/vsaiagent/orchestration/RequestOrchestrator.java`
- Test: `src/test/java/com/vs/vsaiagent/orchestration/RequestOrchestratorTest.java`

**Interfaces:**
- Produces: `FieldResearchEvidence(String capability, String status, String source, String observedAt, String summary, String fallbackReason)`。
- Produces SSE `evidence_recorded`，负载字段为 `evidence`；`status` 只能是 `available`、`unavailable`、`blocked`。

- [ ] **Step 1: 写失败测试**：断言企业外勤请求依次产出 `route_selected`、`capability_started`、至少一条 `evidence_recorded`、`final_completed`。
- [ ] **Step 2: 运行测试确认失败**：运行 `mvnw.cmd -Dtest=RequestOrchestratorTest test`，预期缺少 `evidence_recorded`。
- [ ] **Step 3: 最小实现**：新增不可变证据记录，编排器在能力结果后发射 `evidence_recorded`，并把状态、来源和降级原因保存在负载。
- [ ] **Step 4: 运行测试确认通过**：再次运行相同 Maven 测试。
- [ ] **Step 5: 提交**：`git add` 以上文件并提交 `feat: emit field research evidence events`。

### Task 2: 实现受限企业外勤调研 Skill 与确定性只读工具

**Files:**
- Create: `src/main/java/com/vs/vsaiagent/skill/builtin/EnterpriseFieldResearchSkill.java`
- Create: `src/main/java/com/vs/vsaiagent/agentplatform/tool/impl/FieldWeatherSummaryAgentTool.java`
- Create: `src/main/java/com/vs/vsaiagent/agentplatform/tool/impl/PublicTransitRiskAgentTool.java`
- Create: `src/main/java/com/vs/vsaiagent/agentplatform/tool/impl/FieldResearchBriefAgentTool.java`
- Modify: `src/main/java/com/vs/vsaiagent/tools/ToolRegistration.java`
- Test: `src/test/java/com/vs/vsaiagent/skill/builtin/EnterpriseFieldResearchSkillTest.java`

**Interfaces:**
- Consumes: 地点、日期、任务目标。
- Produces: 带 `source=演示数据`、`observedAt`、`status` 的结构化天气/风险/汇总结果。
- Requires tool whitelist: `field_weather_summary`、`public_transit_risk`、`field_research_brief`。

- [ ] **Step 1: 写失败测试**：断言 Skill 只能选择三个只读工具、输出同时含上午建议、下午建议、证据和限制；断言不存在天气或风险时输出 `unavailable` 而非低风险结论。
- [ ] **Step 2: 运行测试确认失败**：运行 `mvnw.cmd -Dtest=EnterpriseFieldResearchSkillTest test`。
- [ ] **Step 3: 最小实现**：添加确定性演示工具，Skill 仅使用声明白名单聚合结果，所有结果明确标注演示来源与获取时间。
- [ ] **Step 4: 运行测试确认通过**：运行相同测试并执行 `mvnw.cmd -Dtest=ToolGovernanceServiceTest test`。
- [ ] **Step 5: 提交**：提交 `feat: add governed field research skill`。

### Task 3: 接入不可逆导出确认与后端阻断事件

**Files:**
- Create: `src/main/java/com/vs/vsaiagent/agentplatform/tool/impl/TripApprovalExportAgentTool.java`
- Modify: `src/main/java/com/vs/vsaiagent/tools/ToolRegistration.java`
- Modify: `src/main/java/com/vs/vsaiagent/tools/governance/ToolGovernanceService.java`
- Test: `src/test/java/com/vs/vsaiagent/tools/governance/ToolGovernanceServiceTest.java`
- Test: `src/test/java/com/vs/vsaiagent/agentplatform/adapter/AgentToolCallbackGovernanceTest.java`

**Interfaces:**
- `export_trip_approval` 的权限为 `IRREVERSIBLE_WRITE`。
- 未确认时返回 `Decision.confirm(...)` 与 `evidence_recorded.status=blocked`；确认后才返回模拟导出编号。

- [ ] **Step 1: 写失败测试**：断言未确认时导出工具无法执行、不生成编号；确认时得到 `APPROVAL-DEMO-...`，且联系人内容不会出现在理由或日志摘要。
- [ ] **Step 2: 运行测试确认失败**：运行两个治理测试，预期工具不存在或未被阻断。
- [ ] **Step 3: 最小实现**：注册写工具、复用现有治理服务确认逻辑、对外只返回脱敏导出摘要；在编排层将拒绝转为 `blocked` 事件。
- [ ] **Step 4: 运行测试确认通过**：重新运行两个治理测试。
- [ ] **Step 5: 提交**：提交 `feat: gate trip approval export behind confirmation`。

### Task 4: 让记忆引用具备稳定 ID、状态与失败降级

**Files:**
- Modify: `src/main/java/com/vs/vsaiagent/memory/MemoryCandidate.java`
- Modify: `src/main/java/com/vs/vsaiagent/memory/MemoryCandidatePipeline.java`
- Modify: `src/main/java/com/vs/vsaiagent/orchestration/RequestOrchestrator.java`
- Test: `src/test/java/com/vs/vsaiagent/memory/MemoryCandidatePipelineTest.java`
- Test: `src/test/java/com/vs/vsaiagent/orchestration/RequestOrchestratorTest.java`

**Interfaces:**
- `memory_candidate` 必须包含 `memoryId`、`type`、`content`、`status` 与 `sensitivity`。
- 仓储失败时发出 `status=failed`，而不是抛出并丢弃最终回答。

- [ ] **Step 1: 写失败测试**：断言偏好记忆事件含非空 ID；模拟写入异常时仍有 `final_completed` 与 `memory_candidate.status=failed`。
- [ ] **Step 2: 运行测试确认失败**：运行记忆与编排测试。
- [ ] **Step 3: 最小实现**：从持久化条目带回 ID；捕获候选处理异常并转为失败事件，保留主响应。
- [ ] **Step 4: 运行测试确认通过**：再次运行两个测试类。
- [ ] **Step 5: 提交**：提交 `feat: cite memory writes and failures`。

### Task 5: 在聊天工作台渲染演示、引用、确认与降级

**Files:**
- Create: `vs-agent-web/src/components/workbench/EvidenceCitation.vue`
- Create: `vs-agent-web/src/components/workbench/EnterpriseDemoPrompt.vue`
- Modify: `vs-agent-web/src/views/workbench/ChatWorkspace.vue`
- Modify: `vs-agent-web/src/components/workbench/InspectorPanel.vue`
- Modify: `vs-agent-web/src/stores/workbench.js`
- Test: `vs-agent-web/scripts/enterprise-field-research-demo.test.mjs`

**Interfaces:**
- `workbench.addEvidence({ capability, status, source, observedAt, summary, fallbackReason })`。
- `workbench.addConfirmation({ tool, status, reason, confirmationToken })`。
- 演示按钮仅向输入框填入查询，绝不自动发送。

- [ ] **Step 1: 写失败测试**：静态/组件测试断言演示按钮不会调用 `sendMessage`；SSE `evidence_recorded` 会写入 store；`blocked` 显示确认入口；`unavailable` 显示降级理由；记忆引用显示 ID 和状态。
- [ ] **Step 2: 运行测试确认失败**：运行 `node --test scripts/enterprise-field-research-demo.test.mjs`。
- [ ] **Step 3: 最小实现**：增加演示填入组件和证据卡片，扩展事件处理与检查器；确认操作通过 API 发出新请求并显示后端结果。
- [ ] **Step 4: 运行测试确认通过**：运行本测试及现有 `memory-journey.test.mjs`、`workbench-skill-and-chinese.test.mjs`。
- [ ] **Step 5: 提交**：提交 `feat: present enterprise field research evidence`。

### Task 6: 端到端演示、故障注入与运行时验证

**Files:**
- Create: `docs/ENTERPRISE_FIELD_RESEARCH_DEMO_RUNBOOK.md`
- Modify: `docker-compose.yml`（仅在需要显式演示开关时）
- Test: `vs-agent-web/scripts/enterprise-field-research-demo.test.mjs`

- [ ] **Step 1: 写失败测试**：为三种结果（available、unavailable、blocked）补充期望事件序列。
- [ ] **Step 2: 运行测试确认失败**：运行前端演示测试。
- [ ] **Step 3: 最小实现**：增加确定性的演示故障切换方式和运行手册，涵盖正常、天气不可用、未确认导出、确认导出、记忆失败五条路径。
- [ ] **Step 4: 运行验证**：执行后端相关 Maven 测试、前端 node 测试、`npm.cmd run build`、`docker build --pull=false`、运行容器，并用 HTTP/SSE 请求确认实际事件。
- [ ] **Step 5: 提交**：提交 `docs: add enterprise demo runbook`。

### Task 7: 编写 Agent 优化知识点与代码讲解文档

**Files:**
- Create: `docs/AGENT_SYSTEM_OPTIMIZATION_GUIDE.md`
- Test: `vs-agent-web/scripts/validate-agent-optimization-guide.mjs`

**Interfaces:**
- 文档按模块覆盖：真流式与 SSE、Agent 状态隔离、Trace 传播与异步日志、工具安全与确认、Skill 路由、记忆与引用、RAG/上下文边界、批量 embedding、评测与演示验收。
- 每个模块必须包含“问题、设计、关键代码、失败兜底、验证方式、面试表达”。

- [ ] **Step 1: 写失败测试**：验证文档包含所有模块标题、至少八个 Java/Vue 代码块及明确的“已实现/下一步”边界。
- [ ] **Step 2: 运行测试确认失败**：运行 `node --test scripts/validate-agent-optimization-guide.mjs`。
- [ ] **Step 3: 最小实现**：基于真实代码写入模块讲解，代码块使用仓库真实类型/方法，未实现事项明确列为后续计划。
- [ ] **Step 4: 运行测试确认通过**：运行文档测试与 `git diff --check`。
- [ ] **Step 5: 提交**：提交 `docs: explain agent system optimizations`。
