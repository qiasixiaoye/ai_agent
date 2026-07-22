# Agent Runtime Context Engineering：面试追问版

## 1. 设计目标

本轮把原来的“最近十条消息 + 2500 Token 长期记忆”升级为统一的 Context Engineering 策略，覆盖：

- 对话历史何时压缩、保留什么；
- Summary、Semantic、Episodic Memory 如何召回和淘汰；
- Skill 如何先路由后按需加载；
- MCP Tool Schema 如何按查询选择；
- 工具返回过大时如何控制；
- System、History、Memory、RAG、Skill、Tool 分别占多少预算；
- 如何从管理台解释本轮上下文实际组成。

思路参考 Claude Code 的“先清理旧工具输出、再压缩会话、持久规则重新注入”和 `/context` 分类诊断；参考 MemGPT 的工作记忆/外部记忆分层；MCP 工具仍遵循发现、Schema 描述、调用和人工确认的协议边界。

## 2. 默认 Token 分区

模型窗口按 32K 配置，首先划出不可被输入占用的空间：

```text
Context Window                  32768
├─ Output Reserve               4096
├─ Safety Margin                2048
└─ Max Input                   26624
   ├─ Recent History            5000
   ├─ Rolling Summary           1500
   ├─ Semantic Memory           1000
   ├─ Episodic Memory           1000
   ├─ RAG                       5000
   ├─ Loaded Skill Manuals      3500
   ├─ Tool Schemas              2000
   └─ System/User Prompt        actual usage
```

`ContextBudgetManager` 计算 System、User、已加载 Skill、Tool Schema 的实际估算占用。如果剩余空间不足，会按比例缩减 History、Summary、Semantic、Episodic、RAG 五类动态预算，并给出 `NORMAL/HIGH/CRITICAL` 压力等级。

当前 `TokenEstimator` 是模型无关估算器：ASCII 约 4 字符/Token，非 ASCII 约 1.6 字符/Token。它比原来的统一字符换算可靠，但不是模型原生 tokenizer。工程上将估算器隔离成组件，固定模型后可替换为对应 tokenizer，而不修改上层策略。

## 3. 会话历史与自动压缩

Working Memory 同时受两个条件约束：

- 最多保存 20 条消息；
- 最近消息总量超过 History Token Budget 时也触发压缩。

压缩时从最旧消息开始移出，并避免留下缺少对应 User Message 的 Assistant Message。移出的消息形成 `[conversation checkpoint]`，每条最多保留 240 字符，滚动摘要最多 4000 字符。

模型调用时最多请求最近 10 条消息，`HierarchicalChatMemory.get` 再按 5000 Token 从新到旧选择。若单条最新消息本身超限，只保留预算内前缀并加截断标记。

为什么不是直接保留全部历史：长上下文不仅有超限风险，还会出现 attention dilution；近期原文负责局部连续性，摘要负责会话主线，长期记忆负责跨会话事实和事件。

## 4. 长期记忆召回与遗忘

### 4.1 四层职责

| 层级 | 职责 | 写入方式 |
| --- | --- | --- |
| Working | 最近原始对话 | ChatMemory Advisor |
| Summary | 被移出的历史 checkpoint | 自动压缩 |
| Semantic | 用户事实、偏好、稳定约束 | 规则抽取或显式写入 |
| Episodic | 工具执行、任务结果和失败经验 | MCP/执行器回写 |

### 4.2 召回分数

```text
score = lexical relevance × 0.65
      + importance        × 0.20
      + recency           × 0.10
      + access frequency  × 0.05
```

默认召回 Semantic Top 5、Episodic Top 3，但 Top-K 只是候选上限，最终还必须分别放入 1000 Token 的 tier budget。诊断结果会返回每条记忆的分数和 `relevance/importance/recency/frequency` 证据。

如果当前查询没有命中任何 Episodic Memory，会补充一条综合分最高的近期事件作为会话连续性 fallback，并明确标记 `continuityFallback=true`；Semantic Memory 不做这种兜底，避免无关用户事实污染回答。

### 4.3 生命周期

- Semantic/Episodic 每层默认最多 100 条；
- 超限不再简单 FIFO，而是淘汰 `importance + recency + frequency` 保留分最低的项目；
- 召回后更新 `lastAccessAt/accessCount` 并持久化；
- 带相同 `metadata.memoryKey` 的事实采用更新而不是新增，可表达“用户偏好已经改变”；
- 支持按 memoryId 删除，也支持清空整个会话。

## 5. 大工具结果处理

`BudgetedToolCallback` 包在日志装饰器外层：

```text
真实 Tool/MCP
  -> LoggingToolCallback 记录原始结果（数据库字段仍有日志上限）
  -> ToolResultCompressor
  -> 预算内结果返回 LLM
```

单次工具结果默认最多 3500 Token。超限时保留约 72% 头部和 28% 尾部，中间替换为省略标记，同时附带 originalTokens、retainedTokens 和 strategy。这样通常能同时保留结果格式、主要数据和尾部错误/统计信息。

这不是语义摘要。选择确定性 head-tail 是为了避免“为了压缩工具结果再次调用 LLM”造成递归调用、额外成本和摘要幻觉。搜索类工具更理想的做法仍是让工具自身返回分页结果或结构化 Top-N。

MCP 管理接口的确定性直调仍返回完整结果；只有交给 LLM 的 `ManagedCallback` 返回压缩版。这样调试和模型上下文两个需求不会混为一谈。

## 6. Skill 渐进式加载

Skill 使用两阶段 Progressive Disclosure：

1. Router 只读取 name、description、tags、examples、inputs、steps 等轻量元数据进行候选评分；
2. 只为命中的 Top-K Skill 加载 `SKILL.md` 正文和内部步骤。

Skill 正文总预算 3500 Token，单个 Skill 最多 2000 Token。命中 Skill 按路由分数顺序加载；超过总预算的 Skill 会以 `budget-exceeded` 记录在诊断结果中。模型只收到命中 Skill 的 Tool Schema，不会把全部 Skill Schema 塞入上下文。

这与“把所有 Skill 正文常驻 System Prompt”相比，降低了 Token 固定成本，也减少多个操作手册互相干扰。

## 7. MCP 工具目录与 Schema 预算

MCP Tool Catalog 仍由 `ManagedMcpToolService` 负责 TTL 缓存、策略、确认、超时和熔断。在提供给模型前新增查询相关性排序：

- 对 tool name + description 做中英文兼容的字符 bigram 匹配；
- 过滤 policy deny、高风险未确认和熔断中的工具；
- 最多加载 8 个工具；
- 所有 Schema 合计受 2000 Token 预算控制。

因此“服务端发现了多少工具”和“本轮给模型看多少工具”是两个指标。前者属于 Catalog，后者属于 Context Engineering。

## 8. 可解释诊断

接口：

```text
GET /api/context-management/diagnostics
    ?conversationId=demo
    &query=查询天气并生成拍摄计划
    &mode=plain|skills|mcp
```

返回内容包括：

- 模型窗口、输出预留、安全余量和压力等级；
- System/User/Skill/Tool Schema 实际估算量；
- 每类动态预算；
- Working History 实际选择消息数和 Token；
- Summary/Semantic/Episodic 实际占用、召回证据和丢弃原因；
- 实际加载的 Skill 和暴露给模型的 MCP Tool。

前端 Agent Runtime 页面提供“分析完整 Token 预算”，作用类似精简版 `/context`。

## 9. 面试官常见追问

### 为什么需要 Safety Margin？

Token 估算并非模型原生 tokenizer，SDK 还可能加入消息包装和 Tool Calling 协议字段。安全余量吸收估算误差和不可见开销，不能把模型窗口全部分配给业务内容。

### Top-K 和 Token Budget 有什么区别？

Top-K 控制候选数量，Token Budget 控制候选体积。五条很长的记忆仍可能超限，因此召回必须先 Top-K，再按 tier budget packing。

### 为什么 Semantic 和 Episodic 分开？

Semantic 表示稳定事实，应该谨慎召回；Episodic 表示历史事件，更强调时效和任务连续性。两者检索、兜底和淘汰策略不同。

### 为什么不直接用更大的模型窗口？

更大窗口只提高上限，不解决成本、延迟和信息稀释。Context Engineering 的目标是让最相关、最可靠的信息进入注意力窗口。

### 为什么工具结果不全部存进长期记忆？

工具原文可能包含大量临时数据。当前只将 800 字符事件摘要写入 Episodic Memory，避免长期库被一次查询污染；完整结果应进入独立 Artifact/Object Store，通过 URI 按需读取，这是后续生产化方向。

## 10. 仍然明确保留的边界

- Token 是透明的近似估算，不宣称等同于 DashScope tokenizer；
- 长期记忆检索仍是 lexical bigram，不是 embedding/hybrid search；
- checkpoint 是确定性压缩，不是 LLM 结构化摘要；
- 文件型 Memory Store 适合单机演示，不支持多实例事务一致性；
- 超大工具结果暂未落独立 Artifact Store，日志字段也有 8000 字符上限；
- 当前没有为同一轮无限多次 Tool Calling 设置全局累计结果预算，只限制单次结果和 Schema。

这些边界应在面试中主动说明。项目已经具备清晰策略和闭环，但生产化下一步应是模型 tokenizer、Artifact Store、向量/混合检索，以及每轮 Tool Loop 的累计 Token/迭代上限。

## 11. 参考体系

- Claude Code context window: https://code.claude.com/docs/en/context-window
- Claude Code memory: https://code.claude.com/docs/en/memory
- Claude Code working model: https://code.claude.com/docs/en/how-claude-code-works
- MCP Tools specification: https://modelcontextprotocol.io/specification/2025-06-18/server/tools
- MemGPT: https://arxiv.org/abs/2310.08560
