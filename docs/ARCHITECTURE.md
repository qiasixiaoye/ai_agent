# 架构总结：一句话生成工作流，到底是什么结构？

> 给一句话回答："**它不是面向过程的 if-else 堆叠，而是一套借鉴编译器的『三段式 + 分层 + 多设计模式』架构。**"
> 配套通俗讲解见 [`WORKFLOW_BUILDER_OVERVIEW.md`](WORKFLOW_BUILDER_OVERVIEW.md)。

---

## 1. 核心骨架：像编译器一样的三段式

```
   前端 (Parser)             中间表示 (IR)            后端 (CodeGen)
 自然语言一句话  ───────►   WorkflowIR (图)   ───────►   目标产物
                                                         · Dify DSL YAML
 WorkflowPlanner          record(nodes+edges)            · 也可换 n8n / LangGraph / Mermaid …
 (LLM 选工具排顺序)         (目标无关、结构合法)            XxxNodeTemplate
```

- **前端**：`WorkflowPlanner` 把自然语言"解析"成 IR——只做语义决策（选哪些能力、排什么顺序）。
- **IR**：`WorkflowIR(nodes, edges)`，一张目标无关的有向图，把大模型的不确定性挡在这一层之外。
- **后端**：`template` 包把 IR"代码生成"成具体目标。换输出格式 = 换一个后端，前端和 IR 完全复用（就像 LLVM 换目标架构）。

这就是为什么"不用 YAML 还能换别的"——**IR 是解耦点,后端可插拔**。

---

## 2. 分层：每层单一职责

```
Controller   WorkflowBuilderController            REST 入口，起审计请求
    │
Service      WorkflowPlanningService              规划编排：选规划器 + 兜底
             ├─ WorkflowPlanner (接口)            ├─ LlmWorkflowPlanner (主)
             │                                    └─ 规则规划 (兜底)
             ├─ WorkflowDslGenerateService        生成：按 mode/appKind 重新编排拓扑
             └─ WorkflowDslValidateService        校验：IR 合法性 / DSL 合法性
    │
Template     DifyNodeTemplate / AgentNodeTemplate 拼装目标产物（这层才碰 YAML）
    │
Registry     ToolRegistry / SkillRegistry         能力来源统一收口
```

上层依赖**接口**而非实现 → 可单测、可 mock、可在无数据库/无大模型环境下跑核心逻辑单测。

---

## 3. 用到的设计模式（每个都解决一个具体问题）

| 模式 | 在哪 | 解决什么 |
|------|------|----------|
| **策略模式 + 兜底链** | `WorkflowPlanner` 接口，LLM 规划为主、规则规划兜底，失败返回 `Optional.empty()` 自动回退 | 大模型不可靠，但系统必须永远产出可用结果 |
| **中间表示 (IR)** | `WorkflowIR`：LLM 只产 `LlmPlan`，Java `assemble()` 确定性拼出合法图 | 把不确定性挡在 IR 外，图结构一定合法（无环、start/answer 齐全） |
| **模板方法 / Builder** | `DifyNodeTemplate`、`AgentNodeTemplate` 一节点一方法 | 换目标格式只动这一层；DSL 永远结构合法 |
| **注册表模式** | `ToolRegistry` / `SkillRegistry`，规划器从这里读元信息、核对引用 | 加能力零配置；拦截 LLM 臆造的工具 |
| **装饰器模式** | `McpToolLoggingCallback` / `LoggingToolCallback` 包住工具调用 | 可观测与业务逻辑解耦，不侵入工具本身 |
| **适配器模式** | `SkillCallbackAdapter`（Skill→Spring AI ToolCallback）、`AgentToolCallback`（Tool→ToolCallback） | 让异构能力（工具/技能）以统一形态被 ChatClient / MCP 调用 |

---

## 4. 关键解耦决策：规划器与生成器分离

```
规划器 (LlmWorkflowPlanner)          只产「串行直线」IR：start → tool... → llm → answer
        │                            它不知道、也不关心下游会变成什么拓扑
        ▼
生成器 (WorkflowDslGenerateService)  按【工具数量】动态重排拓扑：
        ├─ http  1工具→串行 / ≥2→并行fan-out+汇聚
        └─ agent <4→单智能体 / ≥4→双智能体并行分工
```

好处：**换编排策略、加新拓扑，完全不动规划逻辑**。复杂度被关在生成器一层。

---

## 5. Tool vs Skill：两类能力的内部差异

很多人会问"Skill 和 Tool 有啥区别"——在本架构里它们是**两个抽象层级**：

```
Tool   (AgentTool)        一次原子函数调用：metadata + execute(args) → result
                          例：milkyway_rise(lat,lon,date) → 升起时间

Skill  (Skill/AbstractSkill)   一段有序过程 + 操作手册：
                          · SKILL.md 正文(instructions) = 内部知识/手册
                          · steps[] = 有序内部步骤，步骤可 uses: tool:xxx 调别的工具
                          · execute 按 steps 实际编排多个工具再综合
                          例：astro-shoot-plan 内部依次调 milkyway_rise + light_pollution
                              + cloud_cover，综合成一份拍摄计划
```

- **Tool 是"点"，Skill 是"线"**：Skill 可以是对多个 Tool 的可复用编排 + 领域知识。
- 二者都经适配器变成 Spring AI `ToolCallback`，所以 **agent / MCP / Dify 都能统一调用**——
  但 Skill 的"内部结构"（手册 + 步骤）通过 `GET /api/skills/{name}` 可见，不再是黑盒。

详见 [`SKILL_INTERNALS.md`](SKILL_INTERNALS.md)。

---

## 6. 一段话总结（面试用）

> "它的骨架是编译器式的三段式：规划器是前端，把自然语言解析成一个目标无关的图 IR；模板层是后端，把 IR 代码生成成 Dify DSL，也能换成别的目标。中间用策略模式让 LLM 规划和规则规划互为兜底，用 IR 把大模型的不确定性挡在外面保证图一定合法，用注册表统一能力来源并拦截臆造工具，用装饰器做可观测。规划器只产串行 IR，所有并行/多智能体的拓扑复杂度都收敛在生成器一层。每一层都依赖接口、可独立单测——这不是面向过程的脚本，是分层可演进的架构。"
