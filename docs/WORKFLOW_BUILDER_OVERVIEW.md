# Workflow Builder 通俗讲解：一句话生成 Dify 工作流 + 全链路看得见

> 这份文档讲人话，配箭头和真实例子。讲清楚四件事：
> **① 一句话怎么变成工作流 ② 中间做了哪些聪明的优化 ③ 出了问题怎么看得见 ④ 怎么跟后端和 Dify 接起来。**
> 更细的接口/设计稿见：[`WORKFLOW_BUILDER.md`](WORKFLOW_BUILDER.md)、
> [`mcp-tool-trace-correlation.md`](mcp-tool-trace-correlation.md)、[`complex-input-template.md`](complex-input-template.md)。

---

## 0. 三十秒看懂全流程

```
用户打一句话                "帮我查今晚银河几点升起，再给套拍摄参数"
      │
      ▼  ① 让大模型想清楚要干啥（选工具、排顺序）
   IR 中间表示              start → 工具(银河升起) → 工具(拍摄参数) → LLM 汇总 → 收尾
      │
      ▼  ② 用 Java 模板把 IR 翻译成 Dify 能认的 YAML
   Dify DSL                （这一步绝不让大模型写 YAML，结构对错由代码兜底）
      │
      ▼  ③ 校验 → 通过就存盘
   generated_workflow.yml
      │
      ▼  ④ 一键导入本地 Dify → 生成一个真 App
   Dify App (有 appId)
      │
      ▼  ⑤ 在 Dify 里草稿运行，把每个节点/每次工具调用/报错全captured回来
   审计时间线              ✅start  ✅工具A  ❌工具B(err: 缺参数 fileName)  ...
```

**两条贯穿始终的设计原则**，记住它俩就懂了一半：

1. **大模型只动脑、不动手写 YAML。** 它负责"选哪些工具、怎么排"（产出 IR）；YAML 由 Java 模板拼，所以导入 Dify 几乎不会因为格式错而失败。
2. **加新能力，零改生成代码。** 后端写一个 `@Component` 工具或 Skill，注册表自动收录、MCP 自动暴露，生成器和 Dify 刷新就能用上。

---

## 1. 一句话如何变成工作流

### 1.1 怎么调

```
POST /api/workflow-builder/generate
{ "requirement": "帮我查今晚银河升起时间并给套拍摄参数",
  "mode": "agent", "appKind": "chatflow" }
```

`mode` 和 `appKind` 是**两个互不干扰的开关**（4 种组合都验证过）：

```
mode（怎么调工具）
  http  ── 每个工具 = 一个固定的 HTTP 请求节点，参数后端算好，最稳，不依赖 Dify 插件
  agent ── 工具塞进一个 Agent 节点，由 Dify 运行时的 LLM 自己决定调哪个、调几次

appKind（什么形态的 App）
  chatflow  ── 多轮对话框，有记忆，answer 节点收尾，输入来自 {{#sys.query#}}
  workflow  ── 单轮表单，无记忆，end 节点收尾，输入来自 {{#start.input#}}
```

### 1.2 三步流水线

```
一句话 requirement
   │
   │  ① 规划   WorkflowPlanningService.planDetailed
   │           （LlmWorkflowPlanner 让大模型从注册表里挑工具、排顺序）
   ▼
WorkflowIR ── 统一中间表示，只有 4 类节点：start / tool / llm / answer
   │
   │  ② 生成   WorkflowDslGenerateService.toDslYaml(ir, mode, appKind)
   │           （template 包按 mode/appKind 把 IR 翻译成 Dify YAML）
   ▼
Dify DSL YAML
   │
   │  ③ 校验   WorkflowDslValidateService.validateDsl   →   valid 才存盘
   ▼
WorkflowGenerateResponse{ id, name, ir, dslYaml, valid, errors, warnings, requestId }
```

> **谁干啥要分清**：规划器**只产串行的 4 类节点 IR**（一条直线）；
> "并行、汇聚、多 Agent" 这些花样**全在生成器这一层按工具数量重新编排**，规划器和 IR 根本不知道下游摆成了什么拓扑。好处：换编排策略不用动规划逻辑。

---

## 2. 做了哪些优化（带箭头和例子）

### 优化①：按工具数量自动选最优拓扑，不是一根筋串起来

**http 形态**：

```
0~1 个工具    start ──► [HTTP工具] ──► LLM ──► 收尾                （老老实实串行）

≥2 个工具    start ──┬──► [HTTP工具A] ──┐
                     ├──► [HTTP工具B] ──┤──► 汇聚节点 ──► LLM ──► 收尾
                     └──► [HTTP工具C] ──┘
                     ▲ 一次性 fan-out 并行跑      ▲ fan-in 把结果拼一起
```

为什么敢并行？因为工具参数只引用 `${start}`/常量，互相不依赖对方输出 → 没有数据依赖 → 能并发。

**agent 形态**：

```
<4 个工具    start ──► Agent(挂全部工具，自己决定调哪个) ──► 收尾    （单智能体，最简）

≥4 个工具    start ──┬──► Agent1(工具前一半) ──┐
                     └──► Agent2(工具后一半) ──┴──► 汇聚 ──► 综合LLM ──► 收尾
                     ▲ 两个智能体并行分工
```

### 优化②：汇聚节点用对了类型（踩过坑）

并行后要把多路结果合一,**必须用 `template-transform`，不能用 `variable-aggregator`**：

```
variable-aggregator  →  语义是"取第一个非空值"  →  并行全跑时，除第一路外全被丢掉 ❌
template-transform   →  Jinja2 模板把每一路都拼进去  →  结果一个不少 ✅
```

`aggregatorNode` 拼出来的模板长这样（真实生成）：

```jinja2
【银河升起查询】
{{ r1 }}

【拍摄参数顾问】
{{ r2 }}
```

下游 LLM 只要引用一个变量 `{{#aggregate.output#}}` 就拿到全部结果。

### 优化③：收尾节点跟着 appKind 走（曾导致导入告警）

```
chatflow  →  必须用 answer 节点   →  data.answer = "{{#llm节点.text#}}"        （直接当对话回复）
workflow  →  必须用 end 节点      →  outputs: [{variable:text, value_selector:[llm节点,text]}]
```

坑：早期两种形态都输出 `app.mode=workflow` 却用 answer 节点 → 模式和节点对不上 → 导入告警。
现在 `DifyWorkflowTemplate.root(...,chatflow)` 决定 app.mode，校验也放宽成"answer **或** end 都行"。

### 优化④：agent 形态强行注入"今天几号"（曾把日期编到 2025 年）

agent 形态里工具参数是 **Dify 运行时的 LLM 现填的**，它不知道今天几号 → date 参数瞎编。
解决：`buildAgentInstruction` 在生成时把 `LocalDate.now()` 写进 Agent 指令：

```
当前日期：2026-06-22。若用户未明确指定日期，涉及日期的工具参数一律基于当前日期推算：
"今晚/今天"取当天，"周末"取最近周六，年份必须用当前年份，不要凭记忆臆造。
```

（http 形态没这毛病——后端在生成时就把日期算好填进 HTTP 节点了。）

### 优化⑤：模板拼装本身就是最大的"优化"——看个真实例子

一个 `tool:milkyway_rise` 工具节点，经 `httpRequestNode` 模板翻译后变成：

```yaml
data:
  type: http-request
  method: post
  url: http://localhost:8081/api/agent-platform/tools/milkyway_rise/execute
  headers: "Content-Type:application/json"
  body:
    type: json
    data: '{"arguments": {"date": "{{#sys.query#}}", "location": "..."}}'
```

注意 `${start}` 占位符被自动换成了 `{{#sys.query#}}`（chatflow）或 `{{#start.input#}}`（workflow）——
**同一份 IR，换个 appKind 就接到不同的输入源，模板帮你处理了差异。**

agent 形态下，同一个工具经 `mcpToolEntry` 翻译则是另一副样子（参数交给 LLM 自己填）：

```yaml
- enabled: true
  type: mcp
  provider_name: vs-agent          # 按 server_identifier 解析，不是 uuid
  tool_name: milkyway_rise
  tool_description: "查询指定日期银河升起时间"
  parameters:
    date:   { auto: 1, value: null }   # auto:1 = 让运行时 LLM 自动填
    location: { auto: 1, value: null }
```

### 优化⑥：生成期的错误不再"偷偷吞掉"

`WorkflowPlanner.plan` 把规划失败原因收集进 `diagnostics`，一路透传到响应的 `warnings`，前端「诊断/错误」行直接显示。例如：

```
warnings: [ "形态：agent · chatflow",
            "agent 形态无可挂载工具，已降级为 http 纯 LLM 流程" ]
```

---

## 3. 出了问题怎么看得见（可观测 / trace）

一句话：**每一步都写进两张表，然后在审计页用时间线卡片展示，红色就是出错的地方。**

### 3.1 两张表

```
agent_request_log   ── 一次请求的总账：requestId / scene / status / 总耗时 / 整体 error
       │ 1:N
agent_stage_log     ── 请求里的每一步：stage_type / 名字 / tool_name /
                       入参(input_payload) / 出参(output_payload) / 耗时 / 成败 / error_message
```

`stage_type` 有 6 种：`INPUT / RETRIEVAL / TOOL / MODEL / OUTPUT / ERROR`。
查的时候：`GET /api/observability/requests/{requestId}` → 拿到 `{request, stages}`。

### 3.2 三个地方都会写 trace

```
生成时   /generate           ── scene=workflow-builder-generate，记下规划诊断 + 校验结果
运行时   /workflow-builder/dify-run
                              ── 调 Dify 草稿运行 SSE，每个节点记一条 stage，失败的记 ERROR
工具调用 MCP                  ── Dify→后端工具的每次调用也单独记一条（见下）
```

**运行时**（`DifyRunObserveService`）解析 Dify 的 SSE 事件流（`node_finished` / `agent_log` / `workflow_finished`），
返回 `DifyDraftRunResult{ nodes, agentRounds, status, error, answer, requestId, correlatedToolCalls }`，
前端点「▶ 运行并观测」就能看到每个节点的状态和耗时。

### 3.3 MCP 工具调用：从"完全看不见"到"看得见且能关联"

以前 Dify 的 Agent 经 MCP 调后端工具，后端**一条日志都没有**（capability callback 是裸的）。补了两层：

```
L1  McpToolLoggingCallback   ── 包住每个 MCP 工具，每次被调用就自起一条 scene=mcp-tool-call 的请求
                                （记 tool_name + 入参 + 出参 + 耗时；出错也吞掉不影响工具本身执行）
                                现在 Dify→MCP→后端工具的每次调用都查得到 ✅

L2  correlateMcpToolCalls     ── 一次 dify-run 跑完后，按时间窗 [t0, now]±2s 捞这段时间的 mcp-tool-call 记录，
                                再用 SSE 里的 CALL <工具名> 列表过滤 → 关联到本次运行
                                ⚠️ 是"时间+名字"的启发式关联，不是精确因果对账；并发同名工具可能配错（已注明）
```

> 已知软点：L1 的 `session_id` 落成 `default`（这个 SDK 拿不到 MCP 传输层的 sessionId）。更精确的对账（参数注入令牌）是留白没做。

### 3.4 审计页：时间线卡片，红色就是故障点

关键发现：**错误、入参、出参其实一直在存、API 也一直在返回**，只是旧的表格视图只显示 time/type/name/tool/cost/success 六列，恰好把最有用的三样（错误原因、入参、出参）丢了。纯前端改成时间线后：

```
┌─────────────────────────────────────────────────┐
│ ❌ 运行失败 · 故障点：skill_execute                 │  ← 顶部横幅，直接定位第一个出错的步骤
│    skill[pdf-generation] missing required param: fileName │
├─────────────────────────────────────────────────┤
│ ✅ INPUT  user_input              12ms            │
│ ❌ TOOL   skill_execute           30ms            │  ← 失败卡片标红
│    err: skill[pdf-generation] missing required param: fileName
│    [入参▾] [出参▾]                                 │  ← 点开看完整 payload（就是"日志"）
│ ❌ ERROR  request_error                            │
│                              [ 只看失败 (2) ]      │  ← 一键过滤只看错的
└─────────────────────────────────────────────────┘
```

从工作流运行区点「🔎 查看完整审计轨迹 →」会带 `?requestId=` 深链直达这个页面、自动加载。

> 一条原则（踩坑总结）：**id 要么能一键点开、要么干脆别显示**；别把截断的没用的 id 当装饰摆着。要给用户看的是错误和日志，不是裸 id。

---

## 4. 怎么跟后端服务和 Dify 接起来

### 4.1 跟后端：能力全来自注册表，加工具零配置

```
你写一个 @Component 工具  ──► ToolRegistry 自动收录  ──┐
你写一个 Skill           ──► SkillRegistry 自动收录  ──┴──► 生成器能编排它 + MCP 自动暴露给 Dify
```

- http 形态：工具 → 指向后端 `http://localhost:8081/api/...execute` 的 HTTP 节点。
- agent 形态：工具 → 挂到 Agent 节点的 MCP 条目，描述和参数名都从注册表读。

**加新能力的完整流程**：写好 `@Component`/Skill → 重启后端 → Dify 刷新那个 MCP provider → 完事。全程不碰 DSL 生成代码。

### 4.2 跟 Dify：两条通道

```
通道一  MCP（实时）        后端把所有工具通过 MCP server 暴露 → Dify 在「工具→MCP服务」挂载 → Agent 节点直接调
通道二  Console API（导入/运行）
        POST /workflow-builder/import/{id}   → DifyConsoleClient.importDsl → 得到 appId
        POST /workflow-builder/dify-run      → 草稿运行 SSE，回收节点/Agent/错误轨迹
        GET  /workflow-builder/export/{id}   → 下载 yml（手动导入兜底）
```

本地 Dify 的专属取值（换环境必须改，都能用 `APP_WORKFLOW_BUILDER_*` 环境变量覆盖）：

| 项 | 本机值 |
|----|--------|
| MCP provider 展示名 / **server_identifier**（Dify 按它解析，不是 uuid） | `vs-agent-tools` / **`vs-agent`** |
| Agent 策略 provider / 推荐策略 | `langgenius/agent/agent` / `function_calling`（deepseek 支持） |
| Agent 节点输出变量 | `text`（answer 引用 `{{#<agentId>.text#}}`） |

> ⚠️ **已知挂载坑**（根目录 plan `nested-crafting-storm.md`，**尚未实施**）：
> servlet 的 `context-path: /api` 会让 MCP 在 SSE 握手里通告的端点（`/mcp/message`）跟实际端点（`/api/mcp/message`）对不上 → Dify 卡"授权中"。
> 修法：去掉全局 context-path，改用 `addPathPrefix("/api", forBasePackage("com.vs.vsaiagent"))` 只给 controller 加前缀，让 MCP 的路由留在根路径自洽。**如果 MCP 挂载连不上，先做这个。**

### 4.3 跟前端

- Vue 3 + vue-router，`api.js` 直连 `http://localhost:8081/api`（无反代）。
- `WorkflowStudio.vue`：http/agent + 多轮/单轮两个开关；生成→导入→运行并观测→「🔎 查看完整审计轨迹 →」跳审计页。
- `Observability.vue`：上面 §3.4 的时间线错误分析视图。

---

## 5. 验证状态 & 关键文件

**验证**：4 种组合全部生成 valid + 导入成功；agent 模式实测多轮自主调用 milkyway_rise/astrophoto_settings/exposure_advisor 并整合；多 Agent app 跑通 `start→agent×2→汇聚→llm→answer`；MCP L1/L2 一次 run 关联 6 个工具；错误视图用真实 FAILED 请求验证故障点定位正确。
（偶发 `[SSL: UNEXPECTED_EOF]` 是 Dify 出网查插件市场的瞬时网络错，**重试就好**。）

**关键文件**：

| 关注点 | 文件 |
|--------|------|
| REST 入口 | `workflowbuilder/controller/WorkflowBuilderController.java` |
| 规划（话→IR） | `workflowbuilder/service/{WorkflowPlanningService, LlmWorkflowPlanner, WorkflowPlanner}` |
| 生成（IR→DSL，含并行/多Agent） | `workflowbuilder/service/WorkflowDslGenerateService.java` |
| DSL 模板（真实拼装逻辑） | `workflowbuilder/template/{DifyNodeTemplate, AgentNodeTemplate, DifyWorkflowTemplate}.java` |
| Dify 运行观测 | `workflowbuilder/service/DifyRunObserveService.java` + `dify/client/DifyConsoleClient` |
| MCP 暴露 + L1 | `config/McpCapabilityServerConfig.java` + `observability/tool/McpToolLoggingCallback.java` |
| 可观测落库/查询 | `observability/service/ExecutionLogService`、`observability/controller/ObservabilityController` |
| 前端 | `vs-agent-web/src/views/{WorkflowStudio, Observability}.vue` |

---

## 6. 面试讲解稿（一段一段，可直接说）

**一句话介绍项目。**
我做了一个"自然语言一句话生成 Dify 工作流"的工具。用户输入一句需求，比如"查今晚银河升起时间再给套拍摄参数"，系统会自动规划出工作流、生成 Dify 能直接导入的 DSL、一键导入成一个真实可运行的 App，并且把整个运行过程——每个节点、每次工具调用、每个报错——都采集下来，在审计页用时间线展示。它打通了"自然语言 → 可运行工作流 → 全链路可观测"这条闭环。

**最核心的一个设计决策。**
整个系统我定了一条铁律：**大模型只动脑、不动手写 YAML**。大模型只负责语义层的决策——从工具注册表里挑哪些工具、按什么顺序编排，产出一个我自定义的中间表示 IR；而真正的 Dify DSL 全部由 Java 模板代码拼装。这么做是因为大模型直接吐 YAML 极不稳定，缩进、字段、节点类型动不动就错，导入 Dify 就失败。把"语义规划"和"结构生成"拆开之后，正确性由代码保证，导入成功率几乎是百分之百，这是整个项目能跑通的前提。

**讲一个我做的架构解耦。**
我把规划器和生成器彻底解耦了。规划器只产一条直线式的串行 IR，它根本不知道下游会被摆成什么拓扑；而"要不要并行、要不要拆多智能体"这些编排花样，全部放在生成器层按工具数量动态决定。比如 http 形态下，1 个工具就串行，2 个以上就自动 fan-out 并行再汇聚；agent 形态下，工具少就单智能体挂全部工具，超过 4 个就自动拆成两个智能体并行分工。好处是我换编排策略、加新拓扑，完全不用动规划逻辑，两层各自独立演进。

**讲一个我踩过并解决的坑。**
并行执行后要把多路结果合并，我一开始用了 Dify 的 variable-aggregator 节点，结果发现只能拿到第一路结果、其余全丢了——因为它的语义是"取第一个非空值"，是给 if-else 分支择一用的，不是给并行汇聚用的。我换成 template-transform 节点，用 Jinja2 模板把每一路结果都拼进一个字符串，下游 LLM 引用一个变量就能拿到全部。这个坑很隐蔽，表面能跑通、实际在静默丢数据，是我对着 Dify 真实导出的 DSL 一个字段一个字段比对才定位到的。

**再讲一个细节优化，体现我对运行时行为的理解。**
agent 形态下，工具参数是 Dify 运行时的 LLM 现场填的，我发现它会把日期参数瞎编，甚至编到去年。根因是模型不知道"今天"是哪天。我的解决办法是在生成工作流时，把当前日期 `LocalDate.now()` 直接注入到 Agent 的指令里，明确告诉它"今晚取当天、周末取最近周六、年份必须用当前年"。而 http 形态没这问题，因为日期是后端在生成时就算好填进去的。这个细节说明同样一个工具，在两种编排形态下数据来源完全不同，需要分别处理。

**讲我在可观测性上做的事，这是项目的另一半价值。**
光能生成不够，跑挂了得能查。我设计了两张表——请求总账和阶段明细——把生成期、运行期、以及 Dify 经 MCP 回调后端工具的每一次调用全部记下来。特别是 MCP 这一段，原来是完全的黑盒，后端一条日志都没有，我加了一层装饰器 McpToolLoggingCallback 把每次工具调用包起来记账，并且通过时间窗口加工具名做启发式关联，把"这次工作流运行"和"它触发的后端工具调用"对应起来。我也很清楚这是关联不是精确因果对账，并发同名工具可能配错，所以我在数据里如实标注了这一点，没有把启发式结果包装成确定结论。

**讲一个我从用户反馈里学到的产品判断。**
我一开始在前端把审计 id 截断了显示出来当装饰，用户直接说"这 id 我根本没法用"。我反思后意识到：错误原因、入参、出参这些最有用的信息其实一直在库里、API 也一直在返回，只是旧的表格视图把它们丢了，只显示了状态和耗时。我纯前端把它改成时间线卡片——顶部红色横幅直接定位故障点和错误原因，每个失败步骤标红、能展开看完整入参出参，还能一键只看失败的。从这件事我总结出一条原则：**id 要么能一键点开跳转、要么干脆别显示**，给用户看的应该是可操作的错误和日志，不是没用的裸 id。

**讲系统的可扩展性。**
整套系统加新能力是零成本的。后端开发只要写一个标了 `@Component` 的工具或一个 Skill，它就被注册表自动收录、被 MCP 自动暴露给 Dify，生成器也立刻能编排它——全程不用改任何 DSL 生成代码。这得益于我把"能力来源"统一收口到注册表，生成器和 Dify 都从注册表读元信息，工具的描述、参数名都不用手填。

**如果让我总结这个项目最大的价值。**
它把一个原本需要懂 Dify、会拖拽节点、会配 DSL 的活儿，变成了"说一句话"。技术上的关键不是用了多炫的模型，而是几个工程判断：用中间表示隔离大模型的不确定性、用模板保证结构正确、用分层解耦让编排策略可演进、用全链路 trace 让黑盒变透明。这些加起来才让"一句话生成工作流"从一个 demo 变成真正能用、能排错、能扩展的东西。
