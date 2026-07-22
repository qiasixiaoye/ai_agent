# Agent Runtime 核心知识与面试说辞

## 1. 整体结构

项目中的 Agent Runtime 主要由七个模块组成：

```text
用户请求
  -> 上下文窗口管理
  -> 分层记忆召回
  -> Skill 路由与按需加载
  -> LLM 推理
  -> Tool Calling / MCP 工具调用
  -> 多步骤任务或 Workflow 执行
  -> 执行日志与记忆回写
```

各模块解决的问题：

| 模块 | 解决的问题 |
| --- | --- |
| Context Window | 哪些内容进入本轮模型上下文，各自占多少 Token |
| Memory | 历史消息怎么保留，长期事实和任务经历怎么召回 |
| Skill | 复合能力怎么注册、命中并按需加载操作手册 |
| Tool Calling / MCP | 模型怎么调用外部工具，工具如何治理和复用 |
| Task Orchestration | 多个工具如何按顺序执行并传递结果 |
| Workflow Builder | 如何把自然语言转换成可执行工作流 |
| Observability | 如何定位一次请求经过了哪些步骤、哪里失败 |

---

## 2. 上下文窗口管理

### 2.1 知识点

模型的 Context Window 不只包含聊天记录，还包括：

- System Prompt；
- 当前用户输入；
- 最近对话历史；
- 历史摘要；
- Semantic/Episodic Memory；
- RAG 检索结果；
- Skill 操作手册；
- Tool 名称、描述和 JSON Schema；
- Tool Calling 产生的工具结果；
- 模型输出所需的预留空间。

如果只限制“最近消息条数”，无法保证请求不超限。因为十条很长的消息可能比几十条短消息占用更多 Token，所以项目同时使用条数上限和 Token Budget。

### 2.2 项目中的具体做法

默认按 32K 窗口规划：

```text
总窗口                         32768
├─ 模型输出预留                 4096
├─ 安全余量                     2048
└─ 最大输入                    26624
   ├─ 最近对话历史              5000
   ├─ 历史摘要                  1500
   ├─ Semantic Memory           1000
   ├─ Episodic Memory           1000
   ├─ RAG                       5000
   ├─ Skill 正文                3500
   ├─ Tool Schema               2000
   └─ System/User Prompt        按实际内容计算
```

`ContextBudgetManager` 先估算固定内容，再计算剩余空间。如果动态内容预算之和超过剩余空间，就按比例缩减 History、Summary、Semantic、Episodic 和 RAG 的预算。

Token 估算被封装在 `TokenEstimator` 中：

```text
ASCII 字符：约 4 字符 / Token
非 ASCII 字符：约 1.6 字符 / Token
```

最终会生成 `ContextBudgetPlan`，其中包含：

- contextWindowTokens；
- outputReserveTokens；
- safetyMarginTokens；
- fixedInputTokens；
- categoryBudgets；
- utilization；
- pressureLevel。

### 2.3 面试说辞

> 我没有只用“最近十条消息”控制上下文，因为消息条数和 Token 数不是一回事。我在项目里做了一个统一的 ContextBudgetManager，先从模型窗口里扣掉输出预留和安全余量，再分别给历史消息、摘要、长期记忆、RAG、Skill 正文和 Tool Schema 分配预算。每个模块在把内容放进 Prompt 前，还会执行自己的二次裁剪。这样上下文快满时，不是简单把最早消息全部删除，而是按类别逐步缩减。

### 2.4 可能追问

#### 为什么需要输出预留？

模型的输入和输出共享上下文窗口。如果输入占满窗口，即使请求能发出去，也可能没有足够空间生成完整回答。因此项目固定预留 4096 Token 给输出。

#### 为什么还需要安全余量？

因为 SDK 可能加入消息包装、Tool Calling 协议字段，Token 估算也可能有误差。安全余量用于吸收这些不可见开销。

#### 上下文不足时先删什么？

项目先限制单次工具结果和 Tool Schema，再压缩旧历史；长期内容按 Summary、Semantic、Episodic 的独立预算装配。最近用户问题、System Prompt 和输出预留不会被普通记忆挤占。

#### 为什么不用字符数直接截断？

中文、英文和 JSON 的字符与 Token 比例不同。字符截断可以作为底层手段，但预算决策应该以 Token 为单位。

#### 怎么知道本轮上下文用了多少？

项目提供 `/context-management/diagnostics`，可以看到固定输入、动态预算、历史消息实际 Token、命中的记忆、加载的 Skill 和暴露的 MCP Tool。

---

## 3. 分层记忆管理

### 3.1 知识点

项目把记忆分成四层：

| 层级 | 内容 |
| --- | --- |
| Working Memory | 最近的原始对话消息 |
| Summary Memory | 被移出 Working Memory 的历史摘要 |
| Semantic Memory | 用户事实、偏好和稳定约束 |
| Episodic Memory | 历史任务、工具结果和失败经历 |

Working 和 Summary 解决长会话问题；Semantic 和 Episodic 解决跨轮、跨会话的信息复用问题。

### 3.2 历史消息如何压缩

Working Memory 有两个触发条件：

- 消息超过 20 条；
- 消息总量超过 5000 Token。

触发后：

1. 从最旧消息开始移出；
2. 尽量保持 User/Assistant 消息配对；
3. 每条旧消息截取最多 240 字符；
4. 追加到 `[conversation checkpoint]`；
5. checkpoint 最多保存 4000 字符；
6. 调用模型时最多读取最近 10 条，并再次按 Token 裁剪。

### 3.3 长期记忆如何召回

召回分数由四部分组成：

```text
score = relevance × 0.65
      + importance × 0.20
      + recency × 0.10
      + accessFrequency × 0.05
```

- relevance：当前问题和记忆内容的字符 bigram 相关性；
- importance：写入时指定的重要程度；
- recency：越新的记忆得分越高；
- accessFrequency：经常被使用的记忆获得少量加分。

召回过程：

```text
候选记忆
  -> 计算综合分
  -> Semantic Top 5 / Episodic Top 3
  -> 按各层 Token Budget 装配
  -> 更新 accessCount 和 lastAccessAt
  -> 返回召回原因
```

当 Episodic Memory 没有直接命中时，会补一条综合分最高的近期事件，用于保持任务连续性，并标记 `continuityFallback=true`。Semantic Memory 不使用这种兜底，避免无关用户事实进入回答。

### 3.4 记忆如何更新和删除

- Semantic/Episodic 每层最多保存 100 条；
- 超限后按 importance、recency、frequency 计算保留价值；
- 优先删除保留价值最低的记录；
- 相同 `metadata.memoryKey` 表示同一个事实，新内容会更新旧内容；
- 支持按 memoryId 删除单条记忆；
- 支持清空整个 conversation；
- conversationId 经过 SHA-256 后作为文件名；
- 写文件使用会话锁、临时文件和原子移动。

### 3.5 面试说辞

> 我的记忆模块不是单纯保存聊天记录，而是分成 Working、Summary、Semantic 和 Episodic 四层。最近消息保留原文，旧消息压成 checkpoint；用户偏好进入 Semantic Memory，工具执行和任务结果进入 Episodic Memory。召回时不只看文本相关性，还加入重要性、时间衰减和访问频率，先做 Top-K，再按每一层的 Token Budget 放进上下文。工具执行结果写入 Episodic Memory 后，后续相似问题可以再次召回，这样形成了工具调用和长期记忆之间的闭环。

### 3.6 可能追问

#### Semantic Memory 和 Episodic Memory 有什么区别？

Semantic Memory 表示相对稳定的事实，例如“用户主要使用 Java”；Episodic Memory 表示发生过的事件，例如“上次网页搜索失败并触发了熔断”。前者强调事实一致性，后者强调时间和任务连续性。

#### 为什么不能只使用向量数据库保存所有对话？

原始对话、稳定事实和任务事件的生命周期不同。如果全部混在一个向量库里，容易召回重复消息或过期事件。分层后可以给每类内容设置不同的写入、召回和淘汰规则。

#### 用户偏好改变怎么办？

写入 Semantic Memory 时可以携带 `memoryKey`。相同 key 的新事实会覆盖旧事实，而不是不断追加冲突记录。

#### 记忆越积越多怎么办？

每层有数量上限，超限时不做简单 FIFO，而是根据重要性、时效和访问频率删除保留价值最低的记录。

#### 为什么工具失败也要写进 Episodic Memory？

失败记录可以帮助后续任务避免重复尝试同一个不可用工具，也方便解释之前为什么进行了降级或熔断。

---

## 4. Skill 管理、命中和加载

### 4.1 知识点

Tool 和 Skill 的区别：

```text
Tool：一次原子函数调用
Skill：操作手册 + 参数约束 + 内部步骤 + 执行实现
```

例如：

- `image_search` 是 Tool；
- `astro-shoot-plan` 是 Skill，因为它内部还会调用多个工具并整合结果。

Skill 由两部分组成：

- `SKILL.md`：YAML Front Matter 和 Markdown 操作手册；
- Java `Skill` 实现：真正执行参数校验和内部步骤。

### 4.2 Skill 元数据

`SkillMetadata` 包含：

- name、displayName、description；
- version、sourceType；
- tags、examples；
- inputs、outputs；
- timeoutMs；
- instructions；
- steps。

启动时，`SkillScanner` 解析 `SKILL.md`，注册到 `SkillRegistry`。

### 4.3 Skill 如何命中

`WeightedSkillRouter` 对多个字段分别评分：

```text
名称命中/相似度
+ 标签命中
+ 示例匹配
+ 描述匹配
+ 输入参数和步骤结构匹配
```

默认参数：

```text
Top-K = 3
Threshold = 0.24
```

路由结果包含：

- selectedSkillNames；
- 每个候选的 score；
- 是否被选中；
- name、tag、example、description 等评分原因。

### 4.4 Skill 如何按需加载

Skill 使用两阶段加载：

```text
阶段一：只使用轻量元数据完成路由
阶段二：只加载命中 Skill 的正文和步骤
```

预算规则：

- 所有命中 Skill 正文总预算 3500 Token；
- 单个 Skill 最多 2000 Token；
- 按路由得分顺序加载；
- 超限 Skill 记录为 `budget-exceeded`；
- 只向模型暴露命中 Skill 的 Tool Schema。

`SkillCallbackAdapter` 最终把 Skill 转换为 Spring AI `ToolCallback`，完成 JSON 参数解析，并从 `TraceContext` 中透传 traceId、requestId 和 sessionId。

### 4.5 面试说辞

> 我把 Skill 设计成带操作手册的复合能力，而不是普通函数。Skill 启动时从 SKILL.md 解析名称、描述、标签、输入输出和内部步骤。运行时先通过 WeightedSkillRouter 对元数据做可解释评分，只选择超过阈值的 Top-K；命中后才加载 Markdown 正文，并且限制单 Skill 和全部 Skill 的 Token。最后通过 SkillCallbackAdapter 转成 ToolCallback，让模型可以像调用工具一样调用 Skill。这样 Skill 数量增加后，不需要把全部 Skill 正文和 Schema 都放进上下文。

### 4.6 可能追问

#### Skill 路由为什么不直接交给 LLM？

第一阶段使用确定性路由，可以减少一次模型调用，结果可重复，也方便做离线评测。复杂场景可以在候选召回后再加 LLM rerank，而不是一开始就让模型遍历所有 Skill。

#### Skill 和 Workflow 有什么区别？

Skill 是可复用的领域能力单元，通常完成一种固定目标；Workflow 是一次业务任务的流程图，可以组合多个 Tool、Skill 和 LLM 节点。

#### Skill 正文为什么不能全部放进 System Prompt？

Skill 增多后会产生固定 Token 成本，也可能让多个操作手册互相干扰。元数据常驻、正文按需加载更适合能力规模增长。

#### 怎么判断路由效果？

项目提供固定评测集，记录 expectedSkill 和实际 Top-K 命中情况，可以计算命中率并调整权重、阈值和示例。

#### 一个问题命中多个 Skill 怎么办？

路由结果按得分排序，在 Top-K 和正文总预算内依次加载；模型再根据每个 Skill 的描述和参数决定具体调用哪个。

---

## 5. Tool Calling 与 MCP

### 5.1 Tool Calling 知识点

Tool Calling 的本质不是让模型直接执行 Java 方法，而是：

```text
Java 工具定义
  -> Tool 名称、描述、JSON Schema
  -> LLM 生成 toolName + arguments
  -> 应用程序执行工具
  -> Tool Result 回填 LLM
  -> LLM 生成最终回答
```

项目中有两套工具抽象：

- Spring AI `@Tool/ToolCallback`：用于模型自主调用；
- 自研 `AgentTool + ToolRegistry`：用于确定性任务编排。

`AgentToolCallback` 负责把自研 AgentTool 转换成 Spring AI ToolCallback，使同一个工具可以在 Assistant、Task、Workflow 和 MCP 中复用。

### 5.2 MCP 知识点

MCP 解决的是不同应用之间的工具发现和调用协议。项目同时支持：

- MCP Client：消费远端 MCP Server 提供的工具；
- MCP Server：把本地 AgentTool 和 Skill 暴露给 Dify。

MCP Catalog 和本轮模型工具集是分开的：

```text
服务端全量工具
  -> ToolCallbackProvider
  -> Catalog 缓存
  -> Policy 过滤
  -> Circuit 状态过滤
  -> 当前问题相关性排序
  -> Schema Token Budget
  -> 本轮暴露给模型的工具
```

### 5.3 工具治理的具体操作

- Catalog 默认缓存 30 秒；
- 最多向模型暴露 8 个 MCP 工具；
- Tool Schema 总预算 2000 Token；
- terminal、shell 等工具默认禁止；
- delete、write、send、deploy 等操作需要人工确认；
- 单工具默认超时 15 秒；
- 连续失败 3 次后熔断 60 秒；
- token、password、secret、apiKey 等字段进入日志前脱敏；
- 工具成功或失败事件写入 Episodic Memory。

### 5.4 大工具结果怎么处理

单次返回给模型的工具结果最多 3500 Token：

```text
工具原始结果
  -> LoggingToolCallback 记录原始调用
  -> ToolResultCompressor 估算 Token
  -> 未超限：原样返回
  -> 超限：保留约 72% 头部和 28% 尾部
  -> 加入 originalTokens、retainedTokens、strategy
  -> 返回给模型
```

管理接口的确定性 MCP 调用仍返回完整结果；只有交给 LLM 的 `ManagedCallback` 返回压缩版本。

### 5.5 面试说辞

> Tool Calling 这一层我做了两套适配：Spring AI 的 ToolCallback 用于模型自主选工具，自研 AgentTool Registry 用于任务编排，再通过 Adapter 把两套接口打通。MCP 负责跨应用发现和调用这些工具，但我没有把 MCP Server 的全部工具直接交给模型，而是先经过权限、风险、熔断和查询相关性筛选，再受 Tool Schema Token Budget 限制。工具返回过大时，日志装饰器先记录原始调用，外层压缩器只把预算内结果返回模型，避免一次工具调用占满上下文。

### 5.6 可能追问

#### Tool Calling 和 MCP 有什么区别？

Tool Calling 是模型如何生成函数调用；MCP 是客户端和服务端如何用统一协议发现、描述和调用工具。一个解决模型交互，一个解决系统连接。

#### 为什么 MCP Catalog 需要缓存？

工具发现没有必要每次模型调用都重新执行。TTL 缓存可以降低远端调用和目录构建开销，同时保留手动刷新能力。

#### 为什么要做熔断？

如果远端 MCP Tool 连续失败，继续调用只会增加延迟并浪费 Token。达到阈值后暂时停止暴露该工具，冷却时间结束后再恢复。

#### 高风险工具怎么处理？

高风险工具默认不进入模型自动调用集合。人工确定性调用时必须传入 `confirmed=true`，并记录调用日志。

#### 工具返回几万字为什么不用 LLM 再总结？

再次调用 LLM 会增加成本，也可能形成递归调用和摘要失真。当前使用确定性 head-tail 压缩；搜索类工具则应优先在工具内部返回 Top-N 或分页结果。

#### 为什么保留头部和尾部？

头部通常包含结果格式和主要数据，尾部经常包含统计、错误或结束状态。保留两端比单纯截取前缀更适合通用工具结果。

---

## 6. 多步骤任务执行

### 6.1 知识点

多步骤任务执行属于确定性 Pipeline，不等于完整 ReAct Agent。

核心对象：

- `TaskStepDefinition`：stepId、toolName、args、required；
- `TaskExecutionContext`：traceId、currentStep、maxSteps、stepResults；
- `ToolExecuteResult`：success、output、error、costMs。

### 6.2 具体操作

```text
TaskExecuteRequest
  -> 创建 TaskExecutionContext
  -> 按顺序遍历步骤
  -> 解析 ${step:s1}
  -> ToolRegistry 查找工具
  -> 参数校验和超时执行
  -> 结果写入 stepResults
  -> required 失败则终止
  -> 最终结果整合
```

默认最多执行 6 步，最大不超过 20 步。

项目中的检索示例：

```text
web_search
  -> image_search
  -> result_summary(
       searchResult=${step:s1},
       imageResult=${step:s2}
     )
```

`web_search` 是必选步骤，`image_search` 是可选步骤。最后通过 LLM Summary Tool 整合两个数据源。

### 6.3 面试说辞

> 多步骤执行部分我没有直接写死方法调用，而是抽象了 TaskStepDefinition 和 TaskExecutionContext。每一步通过 toolName 从 Registry 找工具，前序结果保存在 stepResults 中，后续步骤可以通过 `${step:s1}` 引用。required 步骤失败时立即终止，可选步骤失败后继续，同时限制最大步骤数和单工具超时。这样工具只负责自己的原子能力，状态传递和失败策略统一由编排器处理。

### 6.4 可能追问

#### 这是不是 ReAct？

不是。当前步骤在执行前已经确定，属于确定性 Pipeline。ReAct 是模型根据每次 Observation 动态决定下一步 Action。

#### 为什么不全部用 ReAct？

固定业务流程更适合确定性编排，因为顺序、成本和失败策略容易控制。开放式任务可以增加 ReAct 模式，两种方式不冲突。

#### required 和 optional 有什么用？

required 用于核心数据源，失败后继续执行没有意义；optional 用于增强数据源，失败后仍可以使用已有结果完成降级回答。

#### 前序结果怎么传给后序工具？

执行上下文以 stepId 保存 ToolExecuteResult，参数解析器识别 `${step:<id>}`，替换成对应步骤输出。

---

## 7. Workflow Builder

### 7.1 知识点

Workflow Builder 的关键是引入中间表示 Workflow IR，而不是让 LLM 直接输出 Dify YAML。

```text
自然语言需求
  -> LLM/Rule Planner
  -> Workflow IR
  -> Graph Validation
  -> Java DSL Template
  -> Dify YAML
```

LLM 负责语义规划，Java 负责结构正确性。

### 7.2 Workflow IR

`WorkflowIR` 包含：

- id、name、description；
- nodes；
- edges。

当前节点类型：

- start；
- tool；
- llm；
- answer。

Tool 节点使用：

```text
toolRef = tool:<toolName>
或
toolRef = skill:<skillName>
```

参数模板使用 `${start}` 或 `${nodeId}` 引用前序结果。

### 7.3 规划过程

LLM Planner 只输出较小的结构化计划：

- 工作流名称；
- 有序的能力调用列表；
- 最终 LLM 指令。

Java 随后：

1. 使用 ToolRegistry/SkillRegistry 核对能力；
2. 丢弃模型生成但项目中不存在的工具；
3. 创建节点 ID 和边；
4. 组装 Workflow IR；
5. 校验 IR；
6. 失败时回退 Rule Planner。

### 7.4 DSL 生成和校验

DSL 由 Java Map 和模板生成，支持：

- HTTP 模式：每个能力转换为确定性 HTTP 节点；
- Agent 模式：Dify Agent 节点挂载 MCP 工具；
- chatflow：多轮对话，answer 收尾；
- workflow：单轮运行，end 收尾。

校验内容：

- 必须存在 start；
- 必须存在 answer 或 end；
- 节点 ID 唯一；
- edge 引用的节点必须存在；
- 图中不能有环；
- YAML 必须能解析；
- 必须包含 `workflow.graph.nodes/edges`。

### 7.5 面试说辞

> Workflow Builder 的核心不是让大模型直接生成 YAML，而是先生成结构化 Workflow IR。LLM 只负责从自然语言里选择能力和生成任务指令，Java 会拿 ToolRegistry 和 SkillRegistry 校验这些能力是否真实存在，再确定性地创建节点和边。IR 通过图校验后，才由 Java 模板转换为 Dify DSL。LLM 规划失败时会回退规则规划器，所以这条链路既保留了自然语言理解能力，也有结构正确性的兜底。

### 7.6 可能追问

#### 为什么需要 Workflow IR？

IR 把自然语言规划和 Dify 平台格式解耦。规划、校验、本地执行和 DSL 生成都可以围绕同一个数据模型进行。

#### 为什么不让 LLM 直接写 YAML？

YAML 对缩进、字段和版本要求严格，模型容易生成不存在的节点、错误变量引用或非法字段。Java 模板更适合保证结构稳定。

#### 模型生成不存在的工具怎么办？

Planner 结果必须和 ToolRegistry、SkillRegistry 核对。未注册能力会被过滤，最终 IR 还要经过校验。

#### LLM Planner 调用失败怎么办？

`WorkflowPlanningService` 会记录 diagnostics，然后回退关键词规则规划器，仍然生成基础的 Start → Tool → LLM → Answer 流程。

#### 生成后怎么验证能执行？

一条路径是本地 Dry Run，按 IR 节点逐步执行并返回每步结果；另一条路径是导入 Dify 后运行草稿，并采集节点和 Agent 轮次轨迹。

---

## 8. 可观测性

### 8.1 知识点

一次 Agent 请求不只有模型调用，还包括路由、检索、工具和结果整合。只记录最终回答，很难知道错误发生在哪一层。

项目使用三类 ID：

- traceId：一条完整链路；
- requestId：一次请求；
- sessionId：一个会话。

### 8.2 记录的执行阶段

- user_input；
- memory_context_assemble；
- skill_route；
- skill_context_load；
- RAG retrieval；
- model_generate；
- tool_call；
- managed_mcp_tool_call；
- tool_result_compress；
- workflow planner diagnostics；
- DSL validation；
- final_output；
- request_error。

日志内容包括输入、输出、工具名、执行耗时、成功状态和错误信息。敏感字段在进入工具日志前脱敏。

### 8.3 面试说辞

> Agent 的错误可能发生在 Skill 路由、记忆召回、模型推理、工具执行或者结果整合，所以我把一次请求拆成多个 Stage，并用 traceId、requestId、sessionId 关联。比如 MCP 调用超时后，可以从 request 看到本轮加载了哪些工具，再看 managed_mcp_tool_call 的输入、耗时、熔断状态和错误；工具结果被压缩时也会单独记录 originalTokens 和 retainedTokens。这样排查问题不需要只依赖最终回答。

### 8.4 可能追问

#### traceId、requestId、sessionId 有什么区别？

sessionId 表示长期会话；requestId 表示其中一次用户请求；traceId 用于串联请求内部和可能的下游调用链。

#### 为什么 Memory Recall 也要记录？

模型回答错误不一定是推理问题，也可能是召回了错误记忆。记录候选分数、命中项和丢弃原因后，可以区分检索问题和生成问题。

#### 工具日志怎么避免泄露密钥？

进入日志前对 apiKey、token、password、secret 等常见字段进行模式匹配和替换。

#### 怎么查看当前上下文组成？

调用 `/context-management/diagnostics`，选择 plain、skills 或 mcp 模式，可以查看窗口预算、历史消息、记忆、Skill 和工具 Schema 的占用情况。

---

## 9. 一段完整的面试总述

> 这个项目里我主要做的是一套 Agent Runtime。请求进来后，ContextBudgetManager 先为历史消息、分层记忆、RAG、Skill 和 Tool Schema 分配 Token；记忆分成 Working、Summary、Semantic 和 Episodic 四层，召回时综合相关性、重要性、时效和访问频率。Skill 先基于元数据做 Top-K 路由，命中后才加载 SKILL.md 正文。模型需要外部能力时，通过 Tool Calling 调用本地工具或者 MCP 工具；MCP 工具还会经过权限、人工确认、超时、熔断和 Schema 预算，大结果在返回模型前会进行 Token 压缩。固定业务任务使用 TaskExecutionContext 做多步骤状态传递，更复杂的流程则先生成 Workflow IR，再确定性转换成 Dify DSL。整个过程通过 requestId 和 Stage Log 记录，工具结果还会写入 Episodic Memory，供后续会话召回。
