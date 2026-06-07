# vs-ai-agent 升级路线图

本路线图描述了项目从「基于 Spring AI 的多形态对话/智能体应用」演进为「Skill 驱动 + 可编排 + 可评测的企业级 AI 应用平台」的三阶段升级方案。

每个阶段独立可发布、独立可写进简历，且向下兼容现有的 `AssistantApp / VsManus / agentplatform / knowledgebase / observability` 模块。

---

## 总体分层

```
   ┌─────────────────────────────────────────────────────────┐
   │  Layer 3：评测层（Eval Harness）                          │
   │  数据集 + Runner + Judge + 报告，闭环驱动模型/Prompt 迭代  │
   ├─────────────────────────────────────────────────────────┤
   │  Layer 2：编排层（Dify Workflow + TaskOrchestrator）     │
   │  Dify 做可视化编排 / 自研平台做 Java 侧任务执行           │
   ├─────────────────────────────────────────────────────────┤
   │  Layer 1：能力层（Skill / Tool / MCP）                    │
   │  把现有工具重构成 Skill 形态，统一通过 MCP 对外暴露         │
   ├─────────────────────────────────────────────────────────┤
   │  现有底座：Spring AI + AssistantApp + VsManus + PGVector  │
   └─────────────────────────────────────────────────────────┘
```

依赖方向严格自上而下：上层依赖下层，下层不感知上层，任一层可独立替换。

---

## Phase 1 — 能力层 Skill 化（预计 1–2 周）

**目标**：把硬编码的 Java Tool 升级为「可声明、可热加载、可被外部系统共用」的 Skill 单元，对齐 Anthropic Agent Skill 规范，并通过 MCP 协议统一暴露。

### 1.1 新增能力

| 项 | 内容 |
| --- | --- |
| Skill 抽象 | `Skill` 接口 + `AbstractSkill` 基类 + `SkillMetadata`（解析自 SKILL.md） |
| Skill 注册中心 | `SkillRegistry` 接口 + `InMemorySkillRegistry` 实现，启动时扫描 `skills/` 目录 |
| 适配层 | `SkillCallbackAdapter` 把 Skill 包装成 Spring AI `ToolCallback`，老 `AssistantApp / VsManus` 0 改动 |
| 元数据规范 | 每个 Skill 目录下放一份 `SKILL.md`：`name / description / inputs / outputs / examples / tags` |
| 对外暴露 | 通过 `agentplatform` 现有 REST 接口 + MCP Server 双口子开放 |

### 1.2 迁移路径

老的 `tools/*Tool.java` **保留不删**，新增 `skills/*Skill.java` 与之并存。Skill 化后通过适配器把 `Skill` 转成 `ToolCallback` 注入 `AssistantApp`、`VsManus`。第一批迁移 `PDFGenerationTool` 作样板，其余 7 个工具按 1 个/天的节奏迁移。

### 1.3 验收

- `skills/` 目录至少包含 1 个完整样板（PDF 生成）
- 启动时日志能打出已注册 Skill 列表
- 现有 `/ai/assistant_app/*`、`/ai/manus/*` 接口行为完全一致
- MCP Server 能列出所有 Skill 并被外部 MCP 客户端调用成功

---

## Phase 2 — 编排层接入 Dify（预计 1–2 周）

**目标**：把 Dify 作为 sidecar 服务引入，与自研 `TaskOrchestratorService` 互为上下游。简单/确定性任务走 Java 编排，复杂/常变更 workflow 走 Dify 可视化编排。

### 2.1 部署形态

- Dify 用官方 `docker-compose` 拉起，与主后端共享同一台 PostgreSQL（多建一个 schema 隔离）
- 主后端通过 Dify HTTP API 调 Dify Workflow
- Dify 通过 OpenAPI / MCP 调主后端的 Skill

### 2.2 双向集成

**通道一：Java → Dify 暴露 Skill**

- `agentplatform` 新增 `/agent-platform/skills/openapi.json` 端点导出全量 Skill 的 OpenAPI 描述
- Dify 后台一键导入，之后 Dify 工作流可拖拽使用所有 Skill

**通道二：Java → 反向调用 Dify Workflow**

- `agentplatform.service` 新增 `DifyWorkflowExecutor`
- `TaskOrchestratorService` 步骤定义新增类型 `step.type = dify_workflow, step.workflow_id = xxx`
- 调用时打 `POST /v1/workflows/run`，把 `traceId` 通过 Header 透传给 Dify

### 2.3 一次典型混合编排

```
前端 → AiController → TaskOrchestratorService
                         ├─ step1: 本地 Skill（PDF 生成）
                         ├─ step2: Dify Workflow（多分支审核流）
                         │            └→ Dify 内部又调本地 Skill via MCP
                         └─ step3: 本地 Skill（写入知识库）
观测：每一步都进 ExecutionLogService，trace_id 贯穿 Dify 调用
```

### 2.4 验收

- 一个真实业务示例可走通：建议「合同审查工作流」或「论文综述工作流」
- 主后端日志中能看到 Dify 子步骤的 trace 链路
- Dify 内部可成功调用至少 2 个本地 Skill

---

## Phase 3 — 评测层 Eval Harness（预计 1–2 周）

**目标**：建立支持回归 / 横向对比的评测体系，覆盖 Prompt、模型、Skill、Workflow 四类变化的回归。

### 3.1 模块设计

新增 `vs-aiagent-eval/` 子模块，三件事：

**数据集（Suite）**

YAML/JSON 格式，跟代码一起进仓库：

```yaml
suite: rag_legal_qa
cases:
  - id: case_001
    input: "试用期被辞退能拿到补偿吗？"
    expected_contains: ["试用期", "补偿"]
    expected_citations: ["doc_42", "doc_57"]
    rubric: "答案必须明确区分'有重大过失'和'无过错'两种情形"
```

**Runner**

抽象成 `EvalRunner` 接口，实现 4 种：

- `AssistantAppRunner` — 打到 `AssistantApp` 的 RAG/Tool/MCP 各模式
- `VsManusRunner` — 打到 ReAct 智能体
- `AgentPlatformTaskRunner` — 打到任务编排服务
- `DifyWorkflowRunner` — 打到 Dify 工作流

**Judge**

四种 Judge 组合使用：

- `ExactMatchJudge` — 基线
- `KeywordContainsJudge` — 关键字命中
- `LlmAsJudgeJudge` — 用独立模型按 rubric 打分
- `RagFaithfulnessJudge` — Ragas 风格，判答案对召回上下文的忠实度

### 3.2 报告与 CI

- 命令行：`mvn eval:run -Dsuite=rag_legal_qa -Drunner=assistant_app`
- 前端：`vs-agent-web` 加一个 `Eval.vue` 页面，展示 suite × runner × model 的胜率矩阵、失败 case、指标趋势
- CI：GitHub Actions 跑核心 suite，PR 中输出关键指标同比变化

### 3.3 关键设计点

评测与生产共用同一套 `trace_id` 链路 — 一个 case 跑完 `trace_id` 也写进 `eval_result`，点开就能看到对应那次请求的完整阶段日志。这是项目「端到端可观测」最能体现的地方。

### 3.4 验收

- 至少 2 个 suite（一个 RAG、一个 Agent 任务）跑通
- 前端报告页能看到对比矩阵
- GitHub Actions 在 PR 上能输出评测指标 diff

---

## 时间表（按周）

| 周次 | 阶段 | 关键产出 |
| --- | --- | --- |
| W1 | Phase 1 上 | Skill 抽象 + Registry + 适配器 + PDF Skill 样板 |
| W2 | Phase 1 下 | 剩余 7 个工具迁移 + MCP Server 暴露 + OpenAPI 导出 |
| W3 | Phase 2 上 | Dify docker-compose、PG schema、OpenAPI 导入 Dify |
| W4 | Phase 2 下 | `DifyWorkflowExecutor` + trace 透传 + 业务示例 |
| W5 | Phase 3 上 | 数据集 + Runner + Judge 抽象 + 第一份报告 |
| W6 | Phase 3 下 | 前端 Eval 页面 + GitHub Actions 集成 |

---

## 已识别风险

1. **Dify 不应写主库**：Dify 只读 / 调用，不直接写知识库表，避免双写一致性问题。
2. **MCP 透传 `trace_id`**：MCP 协议本身不强制 metadata，需在 MCP Server 端约定自定义 header / tool 参数透传，并在 `LoggingToolCallback` 主动写入。
3. **LLM-as-Judge 偏差**：被测模型与裁判模型必须异源，否则会自夸。建议被测 `dashscope-plus`，裁判 `dashscope-max` 或换 OpenAI 系。
4. **接口兼容性**：Phase 1 `SkillRegistry` 与旧 `ToolRegistration` 并存一段时间，逐步切换，避免前端回归。
5. **评测模块第一版别追求大而全**：先把「一个 suite → 一个 runner → 一个 judge → 一份报告」跑通再扩。

---

## 参考资料

- Anthropic Agent Skills 规范：<https://docs.claude.com/en/docs/agents-and-tools/agent-skills>
- Dify Workflow API：<https://docs.dify.ai/guides/workflow>
- Ragas RAG 评测指标：<https://docs.ragas.io>
- Spring AI MCP：<https://docs.spring.io/spring-ai/reference/api/mcp/>
