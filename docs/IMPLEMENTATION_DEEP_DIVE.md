# 简历 5 条 Bullet · 源码级实现解读

> 面向"刚拿到代码，想搞清楚每一句简历背后到底写了什么"的读者。
> 每条 bullet 拆成：**(1) 简历原文** → **(2) 一句话目标** → **(3) 涉及文件清单** → **(4) 关键设计** → **(5) 核心代码段** → **(6) 一次完整调用流程**。
>
> 项目内涉及的关键 commit：`efa24d3 / 43a93e1 / 4738dde / 6ca41ba / c53697a / e90916e / d2ee904 / cd17173`。

---

## Bullet 1 — Skill 抽象层 + OpenAPI 反向暴露

### 1.1 简历原文

> 设计 Skill 抽象层（SKILL.md 元数据 + 注册中心 + ToolCallback 适配器），支持工具能力声明式接入；同时通过 OpenAPI 端点将 Skill 反向暴露给 Dify

### 1.2 一句话目标

把"一个工具"从"一段写死的 Java 方法"升级成"一个可声明、可热加载、能被 ChatClient / agentplatform / Dify / MCP 客户端**共用**的能力单元"。同一份 Skill 实现要能服务三个调用方。

### 1.3 涉及文件

```
src/main/java/com/vs/vsaiagent/skill/
├── Skill.java                       接口
├── SkillMetadata.java               元数据 record
├── SkillParam.java                  单个输入/输出 schema
├── SkillContext.java                执行上下文（含 traceId）
├── SkillResult.java                 标准返回
├── SkillSourceType.java             LOCAL / MCP / DIFY / REMOTE_HTTP
├── AbstractSkill.java               基类（校验 + 计时 + 异常包装）
├── registry/
│   ├── SkillRegistry.java           注册中心接口
│   └── InMemorySkillRegistry.java   ConcurrentHashMap 实现
├── loader/
│   ├── SkillMdParser.java           解析 SKILL.md 的 YAML front-matter
│   └── SkillScanner.java            @PostConstruct 扫描 + 注册
├── adapter/
│   └── SkillCallbackAdapter.java    Skill → Spring AI ToolCallback
├── config/
│   └── SkillAutoConfiguration.java  组装 skillTools[] Bean
├── builtin/
│   └── PDFGenerationSkill.java      第一个样板
└── controller/
    ├── SkillController.java         /skills CRUD + 执行
    ├── SkillSummaryVO.java
    ├── SkillDetailVO.java
    └── SkillOpenApiController.java  /skills/openapi.json 反向暴露 ★

src/main/resources/skills/pdf-generation/SKILL.md   YAML front-matter + markdown 正文
```

### 1.4 关键设计

**(a) SKILL.md = "Markdown 头部塞 YAML"**

每个 Skill 目录下有一份 `SKILL.md`。文件头部用 `---` 包裹一段 YAML，下面是普通 markdown 介绍。YAML 字段一一对应 `SkillMetadata` 的 record 字段：

```markdown
---
name: pdf-generation
displayName: PDF 生成
description: Generate a PDF file with given content
version: 1.0.0
tags: [file, document]
inputs:
  - name: fileName
    type: string
    required: true
  - name: content
    type: string
    required: true
timeoutMs: 30000
sourceType: LOCAL
---

# 普通 markdown 正文（给人看，代码不读）
```

为什么这么干？因为这样 **人和机器读同一份文件**：开发者看 markdown 正文，启动器读 YAML 头。规范上对齐了 Anthropic Agent Skills，未来一份 SKILL.md 可以被 Claude / Cursor 等外部 Agent 直接复用。

**(b) Skill 接口 + AbstractSkill 模板方法**

`Skill` 只两个方法：`metadata()` 和 `execute(args, ctx)`。

`AbstractSkill` 是模板方法基类，子类只实现 `defaultMetadata()` 和 `doExecute(args, ctx)`，剩下的 **参数必填校验、计时、异常 → SkillResult.fail** 由基类统一处理。这样每个 Skill 实现都是 30 行以内的纯业务代码。

**(c) 注册中心 = 内存版 ConcurrentHashMap**

`InMemorySkillRegistry` 用 `ConcurrentMap<String, Skill>` 维护 name → Skill。register / find / listAll / listByTag / unregister 都是 O(1)。够 MVP，未来要持久化或多节点同步只需要换一个实现就行。

**(d) 启动时扫 + 注入 = SkillScanner + SkillMdParser**

启动期 `SkillScanner` 用 `@PostConstruct` 触发：
1. 用 `PathMatchingResourcePatternResolver` 找 `classpath:skills/**/SKILL.md`
2. 每个文件丢给 `SkillMdParser.parse` → 抽出 front-matter 中的 YAML 段 → snakeyaml 解析 → `SkillMetadata`
3. 同时 Spring 已经把所有 `@Component` 的 `AbstractSkill` 注入 `List<Skill>`
4. 按 name 配对：如果 SKILL.md 里有对应 metadata，就调 `AbstractSkill.overrideMetadata(md)` 覆盖代码里的默认值
5. 最后所有 Skill 注册到 `SkillRegistry`

为什么用 `@PostConstruct` 而不是 `ApplicationReadyEvent`？因为下面的 `SkillAutoConfiguration` 需要在 bean 创建时就能拿到完整 Registry——`@PostConstruct` 在 bean 初始化时触发，比事件早。

**(e) 适配器 = SkillCallbackAdapter（Skill → Spring AI ToolCallback）**

这是最关键的一层，它让 `LoveApp` / `VsManus` 等老代码**完全不用改**就能用上新 Skill。

`SkillCallbackAdapter` 实现 Spring AI 的 `ToolCallback` 接口，构造时拿一个 `Skill`，对外把它包装成符合 Spring AI 函数调用契约的对象：
- `getToolDefinition()` 用 `SkillMetadata` 拼装出 name + description + JSON Schema
- `call(String toolInput)` 把 JSON 反序列化为 `Map`，调用 `skill.execute(args, ctx)`，把 `SkillResult` 序列化成 JSON 返回
- 在调用前从 `TraceContext.get()` 拉 traceId/requestId/sessionId 填到 `SkillContext`，自动接入 observability

**(f) OpenAPI 反向暴露**

`SkillOpenApiController` 暴露 `GET /skills/openapi.json`，遍历 `SkillRegistry.listAll()`，给每个 Skill 生成一条 `POST /skills/{name}/execute` 的 OpenAPI 3.0 path。Dify 后台「自定义工具集合」可以一键导入这个 URL，之后 Dify 工作流就能拖拽用本系统的所有 Skill。

### 1.5 核心代码段

**Skill 接口**：

```java
public interface Skill {
    SkillMetadata metadata();
    SkillResult execute(Map<String, Object> arguments, SkillContext context);
    default String name() { return metadata().name(); }
}
```

**AbstractSkill 模板方法**（关键 30 行）：

```java
@Override
public final SkillResult execute(Map<String, Object> arguments, SkillContext context) {
    long start = System.currentTimeMillis();
    try {
        validate(arguments);  // 必填参数检查
        Object data = doExecute(arguments == null ? Map.of() : arguments,
                context == null ? SkillContext.empty() : context);
        return SkillResult.ok(data, System.currentTimeMillis() - start);
    } catch (IllegalArgumentException e) {
        return SkillResult.fail(e.getMessage(), System.currentTimeMillis() - start);
    } catch (Exception e) {
        return SkillResult.fail(e.getMessage(), System.currentTimeMillis() - start);
    }
}
```

**SkillScanner 扫描注册**：

```java
@PostConstruct
public void scanAndRegister() {
    Map<String, SkillMetadata> mdByName = scanMarkdown();   // 扫所有 SKILL.md
    for (Skill skill : skills) {                            // skills 是 Spring 注入的全部 Skill bean
        String name = skill.metadata().name();
        SkillMetadata fromMd = mdByName.get(name);
        if (fromMd != null && skill instanceof AbstractSkill abstractSkill) {
            abstractSkill.overrideMetadata(fromMd);          // SKILL.md 覆盖代码默认
        }
        skillRegistry.register(skill);
    }
}
```

**SkillCallbackAdapter 上下文穿透**：

```java
private SkillContext buildSkillContext() {
    TraceInfo info = TraceContext.get();    // 从 ThreadLocal 拉
    SkillContext.Builder b = SkillContext.builder();
    if (info != null) {
        b.traceId(info.traceId()).requestId(info.requestId()).sessionId(info.sessionId());
    }
    return b.build();
}
```

### 1.6 一次完整调用流程

```
[启动期]
ApplicationContext 启动
  ↓
@Component 标注的 PDFGenerationSkill 被实例化
  ↓
SkillScanner.@PostConstruct
  ├─ 扫 classpath:skills/**/SKILL.md → 解析 1 个 SKILL.md
  ├─ Spring 注入 List<Skill> 包含 PDFGenerationSkill
  └─ name=pdf-generation 配对成功 → 覆盖 metadata → register
SkillAutoConfiguration.skillTools(@Bean, @DependsOn("skillScanner"))
  └─ 给每个 Skill 包 SkillCallbackAdapter → ToolCallback[] skillTools

[调用期 - 前端从 /skills 页执行]
浏览器 POST /api/skills/pdf-generation/execute  body={"fileName":"a.pdf","content":"hi"}
  ↓
TraceContextFilter 注入 traceId/requestId/sessionId 到 ThreadLocal
  ↓
SkillController.execute("pdf-generation", args)
  ↓
SkillRegistry.find("pdf-generation") → PDFGenerationSkill
  ↓
PDFGenerationSkill.execute(args, ctx)  ← AbstractSkill 做必填校验 + 计时
  └─ doExecute → iText 写文件到 ${user.dir}/tmp/pdf/a.pdf
  ↓
SkillResult.ok({filePath: "...", message: "..."}, 320ms)
  ↓
ApiResponse.success → 前端结果面板 → 检测 filePath → 显示下载按钮

[Dify 反向调用 - 配好后]
Dify 后台 → 导入 http://your-host/api/skills/openapi.json
  → Dify 解析 spec，拿到 pdf-generation 等所有 Skill
Dify Workflow 拖拽工具节点 → 触发时 POST /api/skills/pdf-generation/execute
  → 走同一条 SkillController 路径，与前端等价
```

---

## Bullet 2 — 一句话生成 Workflow

### 2.1 简历原文

> 基于 ChatClient 实现一句话生成 Workflow：将 SkillRegistry 信息注入 prompt，配合自研变量替换执行器串联 LLM / Skill 节点，可导出 Dify 兼容 YAML

### 2.2 一句话目标

用户输入"把用户输入的话写一份 200 字摘要再生成同名 PDF"，系统自动生成一份可执行的工作流定义（含 LLM 节点 + Skill 节点 + 节点间变量传递），并能立刻执行 + 评测 + 导出 Dify YAML。

### 2.3 涉及文件

```
src/main/java/com/vs/vsaiagent/workflow/
├── WorkflowDef.java                数据模型 record
├── WorkflowNode.java               节点 (id / type / prompt / skillName / args / outputVar)
├── WorkflowEdge.java
├── StepResult.java                 单步结果
├── WorkflowResult.java             整体结果
├── service/
│   ├── WorkflowGenerator.java      LLM 驱动生成器 ★
│   ├── WorkflowExecutor.java       变量替换型解释器 ★
│   ├── WorkflowRegistry.java       内存注册中心
│   └── DifyDslExporter.java        WorkflowDef → Dify YAML
├── runner/
│   └── WorkflowEvalRunner.java     接入 Eval 模块（EvalRunner 实现）
└── controller/
    └── WorkflowController.java     5 个 REST 端点
```

### 2.4 关键设计

**(a) 让 LLM 只能生成合法节点 = 把 SkillRegistry 注入 system prompt**

最容易踩的坑：模型瞎编 skillName。所以 generate 时拼 system prompt 的关键步骤是：

```java
sb.append("可用 Skill 清单：\n");
for (Skill s : skillRegistry.listAll()) {
    SkillMetadata md = s.metadata();
    sb.append("- ").append(md.name()).append(" : ").append(md.description());
    if (!md.inputs().isEmpty()) {
        sb.append(" | inputs: ");
        for (SkillParam p : md.inputs()) {
            sb.append(p.name()).append("(").append(p.type()).append(") ");
        }
    }
    sb.append("\n");
}
sb.append("规则：skillName 必须从上方清单中精确选取，不可杜撰。");
```

模型看到只有 `pdf-generation` 一个 Skill 可选，瞎编的概率就大幅下降。这套做法叫 "**runtime-grounded prompt**"——把运行时状态喂进 prompt 而不是 hard-code。

**(b) 鲁棒 JSON 解析三层降级**

模型输出经常带 markdown code fence、解释文字、半截 JSON 等。`WorkflowGenerator.parseRobust` 三步降级：
1. 直接 `MAPPER.readTree(raw)` 试
2. 用正则匹配 ` ```json ... ``` ` code fence，取里面再试
3. 用正则 `\{[\s\S]*\}` 抓第一段大括号再试

这是给 LLM 输出兜底的常见手法。

**(c) 变量替换型解释器**

`WorkflowExecutor` 是一个微型解释器。维护一个 `Map<String, String> variables`，初始只有 `input`。顺序遍历节点：
1. 对 `prompt` 字段（llm 节点）和 `args` 中每个 String 值（skill 节点）做 `${var}` 替换
2. 执行节点：llm → `chatClient.prompt().user(...).call().content()`；skill → `skillRegistry.find(...).execute(args, ctx)`
3. 把节点 `outputVar` 写回 variables，供后续节点引用
4. 首个失败即中止；记录每步 `StepResult`

变量替换用 `Matcher.quoteReplacement` 防止 `$` 反向引用导致的字符替换 bug。

**(d) 用 ThreadLocal 让 EvalRunner 单例服务多 workflow**

`WorkflowEvalRunner` 是 Spring 单例 bean，但每次评测要服务不同 workflowId。借鉴 Spring 自己的 `RequestContextHolder` 的做法，用 `ThreadLocal<String> currentWorkflowId`：

```java
workflowEvalRunner.setCurrent(id);
try {
    for (case in cases) { ... workflowEvalRunner.run(...) ... }
} finally {
    workflowEvalRunner.clearCurrent();   // 防止 ThreadLocal 泄漏
}
```

**(e) Dify YAML 导出**

`DifyDslExporter.toYaml(def)` 把内部 `WorkflowDef` 翻成 Dify 风格：
- llm 节点 → Dify `llm` 类型节点（带 prompt_template）
- skill 节点 → Dify `tool` 类型节点（指向 vs-ai-agent OpenAPI 提供的 tool_name）
- 头尾自动补 `start` / `end` 节点
- snakeyaml 用 `DumperOptions.FlowStyle.BLOCK` 输出可读 YAML

### 2.5 核心代码段

**Generator 核心 prompt + 调用**：

```java
public WorkflowDef generate(String userPrompt) {
    String systemMsg = buildSystemPrompt();   // 含 Skill 清单
    String raw = chatClient.prompt()
            .system(systemMsg)
            .user("用户需求：" + userPrompt + "\n\n只输出 JSON，不要 markdown 包裹，不要解释。")
            .call()
            .content();
    JsonNode root = parseRobust(raw);   // 三层降级
    return toWorkflowDef(root, userPrompt);
}
```

**Executor 核心循环**：

```java
public WorkflowResult execute(WorkflowDef def, String input) {
    Map<String, String> variables = new HashMap<>();
    variables.put("input", input == null ? "" : input);
    List<StepResult> steps = new ArrayList<>();
    for (WorkflowNode node : def.nodes()) {
        StepResult sr = runNode(node, variables);
        steps.add(sr);
        if (sr.success()) {
            variables.put(node.outputVar(), sr.output());
        } else {
            break;  // 首个失败即中止
        }
    }
    String finalOutput = variables.getOrDefault(def.outputVar(), "");
    return new WorkflowResult(def.id(), allSuccess, finalOutput, lastError, elapsed, steps);
}
```

**变量替换**：

```java
private String render(String tpl, Map<String, String> vars) {
    Matcher m = VAR_PATTERN.matcher(tpl);  // \$\{([a-zA-Z0-9_]+)\}
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
        String key = m.group(1);
        String val = vars.getOrDefault(key, "");
        m.appendReplacement(sb, Matcher.quoteReplacement(val));
    }
    m.appendTail(sb);
    return sb.toString();
}
```

### 2.6 一次完整调用流程

```
用户输入: "把用户输入写一份 100 字摘要"

POST /workflow/generate {prompt}
  → WorkflowController.generate
    → WorkflowGenerator.generate
      → buildSystemPrompt()  // 注入 SkillRegistry 元信息
      → ChatClient.prompt().system(sys).user(prompt).call().content()
      → parseRobust(raw)     // 三层降级解析
      → 拼成 WorkflowDef{nodes=[{llm}, {skill?}], edges=..., outputVar}
    → WorkflowRegistry.save(def)  // 内存存
    → 返回 def
前端: 展示节点图

POST /workflow/{id}/execute {input}
  → WorkflowExecutor.execute(def, input)
    → variables = {input: "Spring AI..."}
    → 节点 n1 (llm):
        prompt = render(n1.prompt, vars)   // ${input} 替换
        out = chatClient.prompt().user(prompt).call().content()
        variables["step1"] = out
    → 节点 n2 (skill):
        args = renderArgs(n2.args, vars)   // ${step1} 替换
        result = skillRegistry.find("pdf-generation").execute(args, ctx)
        variables["step2"] = result.data
    → 返回 WorkflowResult{steps=[s1, s2], output=variables[outputVar]}
前端: KPI + 每步 input/output 折叠面板

POST /workflow/{id}/eval {cases, judge}
  → workflowEvalRunner.setCurrent(id)
    → 对每个 case:
        runner.run(input) → 调 executor.execute
        judge.judge(case, actual) → CaseResult
    → 聚合 SuiteResult
  → workflowEvalRunner.clearCurrent()
前端: PASS/FAIL 表格

GET /workflow/{id}/dify-dsl
  → DifyDslExporter.toYaml(def)
  → 浏览器下载 .yaml 文件
```

---

## Bullet 3 — LLM 评测 Harness

### 3.1 简历原文

> 搭建 LLM 评测 Harness，支持 YAML 数据集 + Runner / Judge 可插拔结构，落地 KeywordContains 与 LLM-as-Judge 两类裁判

### 3.2 一句话目标

把"测一个 AI 应用回答得好不好"做成可重复跑、可对比的工程：数据集 YAML 化进仓库；Runner 抽象决定"被测对象是谁"；Judge 抽象决定"怎么算通过"；两者通过 Spring 自动收集做插件化。

### 3.3 涉及文件

```
src/main/java/com/vs/vsaiagent/eval/
├── EvalCase.java                   单条样例 record
├── EvalSuite.java                  整份数据集 record
├── CaseResult.java                 单条结果
├── SuiteResult.java                聚合结果
├── EvalRunner.java                 ★ Runner 接口
├── EvalJudge.java                  ★ Judge 接口
├── loader/SuiteLoader.java         扫 classpath:eval/suites/*.yaml
├── runner/AssistantAppRunner.java  打到 AssistantApp 的 runner
├── judge/KeywordContainsJudge.java 基线裁判
├── judge/LlmAsJudge.java           LLM-as-Judge ★
├── service/EvalService.java        编排（按 suite.judge() 路由）
└── controller/EvalController.java  REST

src/main/resources/eval/suites/
├── general_qa.yaml                 用 keyword_contains
└── subjective_qa.yaml              用 llm_as_judge
```

### 3.4 关键设计

**(a) Runner / Judge 双抽象 + Spring 自动注入**

`EvalRunner` 接口：`String name()` + `String run(input, chatId)`。
`EvalJudge` 接口：`String name()` + `CaseResult judge(case, actualOutput)`。

`EvalService` 构造时 Spring 自动注入 `List<EvalRunner>` 和 `List<EvalJudge>`，按 `name()` 建 Map：

```java
public EvalService(SuiteLoader loader, List<EvalRunner> runnerList, List<EvalJudge> judgeList) {
    for (EvalRunner r : runnerList) runners.put(r.name(), r);
    for (EvalJudge j : judgeList) judges.put(j.name(), j);
}
```

这是 Spring 里**插件化模式**的标准写法：以后写一个 `VsManusRunner implements EvalRunner` 标 `@Component` 就自动注册，零侵入。

**(b) YAML 数据集 + 字段 `runner` / `judge`**

`SuiteLoader.loadAll()` 扫 `classpath:eval/suites/*.yaml`。suite 顶层带 `runner: assistant_app` / `judge: llm_as_judge` 两字段，`EvalService.run` 按这两个字段查 Map 路由：

```java
EvalRunner runner = runners.get(suite.runner());
EvalJudge judge = judges.getOrDefault(suite.judge(), judges.get(KeywordContainsJudge.NAME));
```

判断不到 judge 时默认回退 `keyword_contains`，保证总能跑。

**(c) KeywordContainsJudge = 基线**

最简单：`expected_contains` 数组里每个关键词都要出现在 actual 中（大小写无关）即通过。命中失败把 missed 关键词带到 `CaseResult.missedKeywords` 里——前端能直观看到"差哪个词"。

**(d) LlmAsJudge = 模板法 + JSON 强约束**

让模型当裁判。三个关键设计：

1. system prompt 强约束输出为严格 JSON：`{"pass": true|false, "reason": "..."}`
2. 用户消息里塞 input + rubric + 期望要点 + 实际答案
3. 解析鲁棒：先直接 readTree；失败用正则 `\{[^{}]*"pass"[^{}]*\}` 抓 JSON 块再试；再失败降级"未通过 + 解析失败"

为啥要正则兜底？因为模型经常输出 ` ```json\n{...}\n``` ` 或者 "Here is my judgment: {...}" 带说明，直接 readTree 会炸。

**(e) 每个 case 用独立 chatId 防上下文串扰**

```java
String chatId = "eval-" + suite.name() + "-" + c.id() + "-" + UUID.randomUUID();
```

否则 ChatMemory 会把前后 case 拼起来污染。

### 3.5 核心代码段

**EvalService 主循环**：

```java
public SuiteResult run(String suiteName) {
    EvalSuite suite = suiteLoader.findByName(suiteName).orElseThrow();
    EvalRunner runner = runners.get(suite.runner());
    EvalJudge judge = judges.getOrDefault(suite.judge(), judges.get("keyword_contains"));

    for (EvalCase c : suite.cases()) {
        String chatId = "eval-" + suite.name() + "-" + c.id() + "-" + UUID.randomUUID();
        String actual = runner.run(c.input(), chatId);    // 真打模型
        CaseResult cr = judge.judge(c, actual);           // 判分
        // ...聚合
    }
    return new SuiteResult(...);
}
```

**KeywordContainsJudge**：

```java
@Override
public CaseResult judge(EvalCase evalCase, String actualOutput) {
    String body = actualOutput == null ? "" : actualOutput.toLowerCase();
    List<String> missed = new ArrayList<>();
    for (String kw : evalCase.expectedContains()) {
        if (!body.contains(kw.toLowerCase())) missed.add(kw);
    }
    boolean pass = missed.isEmpty();
    String reason = pass ? "all keywords matched" : "missing: " + String.join(", ", missed);
    return new CaseResult(evalCase.id(), evalCase.input(), actualOutput, pass, reason, missed, 0L, 0L);
}
```

**LlmAsJudge 关键 prompt + 解析**：

```java
public LlmAsJudge(ChatModel dashscopeChatModel) {
    this.chatClient = ChatClient.builder(dashscopeChatModel)
            .defaultSystem("""
                你是一个严格的评测裁判。给定题目、参考要点和模型答案，按 rubric 判断是否通过。
                只输出严格 JSON：{"pass": true|false, "reason": "简短判定理由"}
                """)
            .build();
}

private static JsonNode parseJson(String raw) {
    try { return MAPPER.readTree(raw.trim()); } catch (Exception ignore) {}
    Matcher m = JSON_BLOCK.matcher(raw);       // \{[^{}]*"pass"[^{}]*\}
    if (m.find()) {
        try { return MAPPER.readTree(m.group()); } catch (Exception ignore) {}
    }
    return null;   // 触发降级
}
```

### 3.6 一次完整调用流程

```
POST /eval/run/general_qa
  → EvalController.run("general_qa")
  → EvalService.run("general_qa")
    → SuiteLoader.findByName → 读 general_qa.yaml
      runner=assistant_app, judge=keyword_contains, 4 cases
    → runners.get("assistant_app") → AssistantAppRunner
    → judges.get("keyword_contains") → KeywordContainsJudge
    → for each case:
        chatId = "eval-general_qa-gen_001-<uuid>"
        actual = AssistantAppRunner.run(c.input, chatId)
              → assistantApp.doChat(input, chatId)
              → 真打 DashScope 拿回答
        CaseResult cr = KeywordContainsJudge.judge(c, actual)
        聚合 passed/failed
    → SuiteResult{suite, runner, judge, total, passed, failed, totalElapsedMs, cases[]}
  → ApiResponse.success
前端: KPI 面板 + case 表格

# 切换到 subjective_qa（用 LLM 裁判）只是 yaml 改 judge 字段
POST /eval/run/subjective_qa
  → 上面流程一样，最后 judge 换成 LlmAsJudge
  → 每个 case 多一次 LLM 调用做评分
```

---

## Bullet 4 — Trace 链路上下文穿透

### 4.1 简历原文

> 基于 Filter + ThreadLocal + 装饰器实现 trace 链路上下文穿透，统一记录对话 / 检索 / 工具 / 评测 各阶段日志

### 4.2 一句话目标

任何一次 HTTP 请求进来，**自动**带上一个 `traceId`，并且**在不改业务代码的前提下**，让 Controller → Service → ToolCallback → Repository 全链路都能拿到这个 trace，最终所有阶段日志都能用 trace 串起来。

这其实是把"分布式追踪 Sleuth / Otel 的核心思想"用 Spring 标配工具自己造个轻量版。

### 4.3 涉及文件

```
src/main/java/com/vs/vsaiagent/observability/
├── context/
│   ├── TraceContext.java                ThreadLocal 持有者
│   └── TraceInfo.java                   record(traceId, requestId, sessionId)
├── config/
│   ├── TraceContextFilter.java          ★ 入口 Filter
│   └── ObservabilitySchemaInitializer.java   启动建表
├── enums/
│   └── ExecutionStageType.java          INPUT/RETRIEVAL/MODEL/TOOL/OUTPUT
├── service/
│   ├── ExecutionLogService.java         统一日志服务接口
│   └── impl/ExecutionLogServiceImpl.java
├── tool/
│   └── LoggingToolCallback.java         ★ 装饰器
├── repository/
│   ├── AgentRequestLogRepository.java
│   └── AgentStageLogRepository.java
├── entity/, dto/, vo/
└── controller/
    └── ObservabilityController.java     查询接口
```

### 4.4 关键设计

**(a) 三层职责分离**

| 层 | 角色 | 代码 |
| --- | --- | --- |
| 入口 Filter | 抽 trace ID / 写 ThreadLocal | `TraceContextFilter extends OncePerRequestFilter` |
| 中转 ThreadLocal | 跨方法持有 trace | `TraceContext` |
| 出口装饰器 | 工具调用前后自动写日志 | `LoggingToolCallback implements ToolCallback` |

业务代码（`AssistantApp.doChat` 等）只需要在关键点调 `executionLogService.logStage(...)`，不需要知道 trace 是怎么来的。

**(b) Filter 自动从 Header 取 / fallback UUID**

`TraceContextFilter` 继承 `OncePerRequestFilter`（Spring 提供，保证一次请求只走一次），在 `doFilterInternal` 里：
1. 从 Header `X-Trace-Id` / `X-Request-Id` / `X-Session-Id` 取；没有就 `UUID.randomUUID()`
2. sessionId 还做了一个兜底：取请求参数 `chatId`（这样前端不用专门加 header，复用现有的 chatId）
3. `TraceContext.set(new TraceInfo(...))`
4. 把 trace ID 同步写回 response header（方便前端在浏览器 DevTools 看）
5. 关键：**`try { chain.doFilter } finally { TraceContext.clear() }`** —— 防止 ThreadLocal 跨请求泄漏（线程池复用同一个线程会出 bug）

**(c) ExecutionLogService 写两张表**

主表 `agent_request_log` 一次请求一行：`startRequest` 写入开始，`finishSuccess/finishFail` 更新结束。
明细表 `agent_stage_log` 一次请求多行：每个 `logStage(...)` 调用插一行。

`startRequest` 时还顺手 `logStage(INPUT, ...)` 记下用户输入，省一次调用。

**(d) LoggingToolCallback 装饰器自动记 TOOL 阶段**

`ToolCallback` 是 Spring AI 用来描述"模型可以调用的函数"的接口。`LoggingToolCallback` 拿一个原始 callback 做 delegate，对外伪装成原 callback，内部在 `call(toolInput)` 前后：

```java
private String doCall(String toolInput, Supplier<String> invoke) {
    long start = System.currentTimeMillis();
    String requestId = TraceContext.get() == null ? null : TraceContext.get().requestId();
    try {
        String output = invoke.get();
        if (requestId != null) {
            executionLogService.logStage(requestId, TOOL, "tool_call",
                    delegate.getName(), toolInput, output,
                    System.currentTimeMillis() - start, true, null);
        }
        return output;
    } catch (Exception e) {
        // 同样落日志，success=false
    }
}
```

`ToolRegistration` 装配 `allTools` 时统一包一层，所有工具调用都自动落 TOOL 阶段：

```java
ToolCallback[] callbacks = ToolCallbacks.from(fileTool, webSearchTool, ...);
for (int i = 0; i < callbacks.length; i++) {
    wrapped[i] = new LoggingToolCallback(callbacks[i], executionLogService);
}
```

**(e) 5 类阶段枚举对齐 AI 应用真实生命周期**

`ExecutionStageType`: `INPUT`（用户输入）/ `RETRIEVAL`（RAG 召回）/ `MODEL`（LLM 生成）/ `TOOL`（工具调用）/ `OUTPUT`（最终输出）。任何一个新模块只需要决定它在哪个阶段，不需要新增枚举。

### 4.5 核心代码段

**TraceContextFilter**：

```java
@Component
public class TraceContextFilter extends OncePerRequestFilter {
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) {
        String traceId = defaultId(req.getHeader(HEADER_TRACE_ID));
        String requestId = defaultId(req.getHeader(HEADER_REQUEST_ID));
        String sessionId = req.getHeader(HEADER_SESSION_ID);
        if (StrUtil.isBlank(sessionId)) sessionId = req.getParameter("chatId");
        if (StrUtil.isBlank(sessionId)) sessionId = "default";
        TraceContext.set(new TraceInfo(traceId, requestId, sessionId));
        resp.setHeader(HEADER_TRACE_ID, traceId);   // 回写 response
        try {
            chain.doFilter(req, resp);
        } finally {
            TraceContext.clear();   // 必须清，否则线程池下一次复用会带脏数据
        }
    }
}
```

**TraceContext**（极薄的 ThreadLocal 包装）：

```java
public final class TraceContext {
    private static final ThreadLocal<TraceInfo> HOLDER = new ThreadLocal<>();
    public static void set(TraceInfo t) { HOLDER.set(t); }
    public static TraceInfo get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }
}
```

**LoggingToolCallback 调用**：

```java
@Override
public String call(String toolInput) {
    return doCall(toolInput, () -> delegate.call(toolInput));
}

private String doCall(String toolInput, Supplier<String> invoke) {
    long start = System.currentTimeMillis();
    String requestId = TraceContext.get() == null ? null : TraceContext.get().requestId();
    try {
        String output = invoke.get();
        if (requestId != null) executionLogService.logStage(requestId, TOOL, ...);
        return output;
    } catch (Exception e) {
        if (requestId != null) executionLogService.logStage(requestId, TOOL, ..., success=false, e.getMessage());
        throw e;
    }
}
```

### 4.6 一次完整调用流程

```
浏览器 GET /api/ai/assistant_app/chat_rag/sse?message=...&chatId=abc
  ↓
[Filter 链]
TraceContextFilter.doFilterInternal:
  traceId = UUID (假设 header 没带)
  requestId = UUID
  sessionId = req.getParameter("chatId") = "abc"
  TraceContext.set(TraceInfo{traceId, requestId, "abc"})
  response.setHeader("X-Trace-Id", traceId)
  ↓
[Controller]
AiController.doChatWithRagSse
  → AssistantApp.doChatWithRagSse(message, "abc")
    → ExecutionLogService.startRequest("chat_rag_stream", "abc", message, modelName)
        → 读 TraceContext.get() → 拿到 traceId/requestId
        → INSERT agent_request_log (status=SUCCESS, started_at=now)
        → INSERT agent_stage_log (INPUT)
    → pgVectorVectorStore.similaritySearch(...)
        → 召回完成
        → executionLogService.logStage(requestId, RETRIEVAL, "rag_recall", ..., elapsed, true)
    → ChatClient.stream() → 流式调 DashScope
        → 完成 doOnComplete
        → executionLogService.logStage(requestId, MODEL, "model_stream_generate", ..., true)
        → finishSuccess
  ↓
[Filter finally]
TraceContext.clear()   // 关键：清干净 ThreadLocal

[Manus 智能体场景，工具调用走另一条路]
VsManus.runStream
  → ToolCallAgent.think → ChatClient.call(prompt).tools(allTools)
    → 模型返回 ToolCall(name=webSearch, args=...)
  → ToolCallAgent.act → toolCallingManager.executeToolCalls(...)
    → 触发 LoggingToolCallback.call(toolInput)
      → 拿 TraceContext.get().requestId  ← 仍然能拿到，因为同一线程
      → invoke.get() → 真调原始 webSearchTool
      → logStage(requestId, TOOL, "tool_call", "webSearch", input, output, elapsed, true)
    → 工具结果回喂模型
```

---

## Bullet 5 — ReAct 智能体 + MCP 工具统一接入

### 5.1 简历原文

> 基于 ReAct 智能体（Thought-Action-Observation 循环）+ Spring AI MCP Client，统一接入本地工具与远端 MCP 工具

### 5.2 一句话目标

把"AI 调一次工具"升级成"AI 多步思考 + 反复用工具直到完成任务"；而且无论工具是本地 Java 类（如 PDF 生成）还是另一个独立进程暴露的 MCP server（如图片搜索），对智能体来说**接口完全一样**。

### 5.3 涉及文件

```
src/main/java/com/vs/vsaiagent/agent/
├── BaseAgent.java         状态机 + 主循环 + SSE 流
├── ReActAgent.java        think + act 抽象
├── ToolCallAgent.java     ★ ReAct 的工具调用版实现
├── VsManus.java           业务壳层 + system/next_step prompt
└── model/AgentState.java  IDLE/RUNNING/FINISHED/ERROR

src/main/java/com/vs/vsaiagent/app/AssistantApp.java
  └─ doChatWithMcp(...)   用 toolCallbackProvider 注入 MCP 工具

src/main/resources/application-xxxxx.yml
  └─ spring.ai.mcp.client.sse.connections.server1.url
      指向独立的 vs-image-search-mcp-server

vs-image-search-mcp-server/                独立子进程
  └─ tools/ImageSearchTool.java            标 @Tool
```

### 5.4 关键设计

**(a) 四层继承链：每层只加一类职责**

```
BaseAgent                          状态机 + 主循环 + SSE
   ↓ extends
ReActAgent                         抽象出 think() / act() 两个方法
   ↓ extends
ToolCallAgent                      think 调 LLM 选工具，act 真执行工具
   ↓ extends
VsManus                            注入 system prompt / next-step prompt
```

每一层做且只做一件事：

- **BaseAgent**：状态机 (`IDLE → RUNNING → FINISHED/ERROR`)、最大步数循环、SseEmitter 流式输出、消息列表内存
- **ReActAgent.step()**：核心 ReAct 循环 = `if (think()) return act();`
- **ToolCallAgent.think()**：把消息列表喂给模型，让它输出 `ToolCall`，返回值 = 是否还需要调工具
- **ToolCallAgent.act()**：用 Spring AI 的 `ToolCallingManager.executeToolCalls` 执行模型选的工具
- **VsManus**：构造时塞业务 prompt + maxSteps=30

**(b) `withProxyToolCalls(true)` 是关键**

```java
this.chatOptions = DashScopeChatOptions.builder()
        .withProxyToolCalls(true)   // 关键
        .build();
```

这个选项告诉 Spring AI："**模型决定要调工具时，不要自动执行**，把决策交回我手里"。这样 think/act 才能分离：think 只是"让模型告诉我要不要调 + 调哪个"，真正的执行由 act 控制。否则 Spring AI 默认会一气呵成调完所有工具，没法做 ReAct 的多步反思。

**(c) `doTerminate` 工具作为退出口**

ReAct 怎么知道"我做完了"？模型自己说不算（容易死循环）。设计上加一个特殊工具 `TerminateTool`，模型选它就视为完成：

```java
boolean doTerminate = toolResponseMessage.getResponses().stream()
        .anyMatch(toolResponse -> toolResponse.name().equals("doTerminate"));
if (doTerminate) setState(AgentState.FINISHED);
```

最大步数 30 是兜底，防止模型不调 `doTerminate` 死循环。

**(d) Spring AI MCP Client：远端 MCP 工具自动注入**

`application.yml` 配 `spring.ai.mcp.client.sse.connections.server1.url=http://localhost:8082`，Spring AI 启动时会：
1. 连到远端 MCP server
2. 调 `tools/list` 拿到所有工具元信息
3. 把每个工具包成一个 `ToolCallback`，组装成一个 `ToolCallbackProvider` bean

业务代码用法跟本地工具几乎一样：

```java
@Resource private ToolCallbackProvider toolCallbackProvider;  // MCP 工具

public String doChatWithMcp(String message, String chatId) {
    ChatResponse r = chatClient.prompt()
            .user(message)
            .tools(toolCallbackProvider)   // ← 注入远端工具
            .call()
            .chatResponse();
    return r.getResult().getOutput().getText();
}
```

对模型而言，远端 MCP 工具和本地 `ToolCallback[]` 工具调用方式完全一致——这就是简历里"统一接入"的含义。

**(e) SSE 流式：每步结果切随机长度小块吐出**

`BaseAgent.runStream` 用 `CompletableFuture.runAsync` 异步执行 ReAct 循环。每个 step 拿到结果后，把字符串切成 2-6 字符的小块 chunked 送出去：

```java
int chunkSize = ThreadLocalRandom.current().nextInt(1, 7);
String chunk = result.substring(j, Math.min(j + chunkSize, result.length()));
sseEmitter.send(chunk);
Thread.sleep(50);
```

这是给前端"打字机效果"用的——避免一整段 step 结果一下闪出来。

### 5.5 核心代码段

**ReActAgent.step（核心循环 = 一行）**：

```java
@Override
public String step() {
    if (think()) return act();
    setState(AgentState.FINISHED);
    return CollUtil.getLast(getMessageList()).getText();
}
```

**ToolCallAgent.think（让模型选工具）**：

```java
@Override
public boolean think() {
    if (StrUtil.isNotBlank(getNextStepPrompt())) {
        getMessageList().add(new UserMessage(getNextStepPrompt()));
    }
    Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
    ChatResponse chatResponse = getChatClient().prompt(prompt)
            .system(getSystemPrompt())
            .tools(this.availableTools)
            .call()
            .chatResponse();
    this.toolCallChatResponse = chatResponse;   // 暂存给 act 用
    AssistantMessage am = chatResponse.getResult().getOutput();
    List<AssistantMessage.ToolCall> toolCalls = am.getToolCalls();
    if (toolCalls.isEmpty()) {                  // 模型没选工具 → 不需要 act
        getMessageList().add(am);
        return false;
    }
    return true;
}
```

**ToolCallAgent.act（真调工具 + 判断终止）**：

```java
@Override
public String act() {
    Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
    ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
    setMessageList(result.conversationHistory());   // 把工具结果塞进消息列表
    ToolResponseMessage trm = (ToolResponseMessage) CollUtil.getLast(result.conversationHistory());
    boolean doTerminate = trm.getResponses().stream()
            .anyMatch(r -> r.name().equals("doTerminate"));
    if (doTerminate) setState(AgentState.FINISHED);
    return trm.getResponses().stream()
            .map(r -> "工具:" + r.name() + " 返回:" + r.responseData())
            .collect(Collectors.joining("\n"));
}
```

**VsManus 业务壳层（30 行装配）**：

```java
public VsManus(ToolCallback[] allTools, ChatModel dashscopeChatClient) {
    super(allTools);
    this.setName("vsManus");
    this.setSystemPrompt("You are VsManus, an all-capable AI assistant ...");
    this.setNextStepPrompt("Based on user needs, proactively select the most appropriate tool ...");
    this.setMaxSteps(30);
    this.setChatClient(ChatClient.builder(dashscopeChatClient)
            .defaultAdvisors(new MyLoggerAdvisor())
            .build());
}
```

### 5.6 一次完整调用流程

```
GET /ai/manus/chat?message="搜索 spring ai 并写 100 字简介"&contentText=""
  ↓
AiController.doChatWithManus
  → new VsManus(allTools, chatModel)   // 每次新实例
  → vsManus.runStream(message, contentText)
      → SseEmitter
      → CompletableFuture.runAsync:
          for (i = 0; i < 30 && state != FINISHED; i++) {
              step()  // ReActAgent.step
                ↓
              think()
                → messageList.add(nextStepPrompt)
                → ChatClient.prompt(messages).tools(availableTools).call()
                → 模型决定调 webSearch
                → toolCallChatResponse 暂存
                → return true
              act()
                → toolCallingManager.executeToolCalls()
                  ↓
                LoggingToolCallback.call("webSearch", {"query":"spring ai"})
                  → TraceContext.get().requestId 拉到 trace
                  → invoke webSearchTool 真调外部 API
                  → logStage(requestId, TOOL, "tool_call", "webSearch", ...)
                → 工具结果 ToolResponseMessage 塞回 messageList
                → 检查是否 doTerminate
                → return "工具:webSearch 返回:..."
              ↓
              "Step 1: 工具:webSearch 返回:..." 切 2-6 字符 SSE 送出
          }
          ↓
          下一步 step：
          think() → 模型看到 webSearch 结果，决定再调 doTerminate
          act() → 设 state=FINISHED
          ↓
          循环退出，SseEmitter.complete

[MCP 工具场景 - doChatWithMcp]
GET /ai/assistant_app/...
  → AssistantApp.doChatWithMcp(message, chatId)
    → chatClient.prompt().user(message).tools(toolCallbackProvider).call()
       └─ toolCallbackProvider 来自 Spring AI MCP Client
          自动连到 http://localhost:8082 拉远端 imageSearch 工具
       └─ 模型选了 imageSearch
          ↓
       Spring AI MCP Client 把调用 RPC 到远端 MCP server
          └─ vs-image-search-mcp-server.ImageSearchTool.search(...)
          └─ 结果通过 SSE 拉回来
       └─ 模型基于结果生成最终回答
```

---

## 附录 A · 几条横向"基础设施"小知识

### A.1 为什么大量用 Java record？

`record` 是 Java 14+ 的不可变数据类。本项目所有"纯数据传输"类型（SkillMetadata、SkillResult、EvalCase、WorkflowDef、StepResult 等）都用 record。好处：
- 自动 equals / hashCode / toString
- 字段不可变，线程安全
- 紧凑构造器可以做参数归一化（如把 null 转 emptyList）

### A.2 为什么 ApiResponse 同时存在 observability/vo 和 knowledgebase/vo 两份？

历史包袱：项目从 LoveApp 教程演进来，knowledgebase 模块独立设计了一个 ApiResponse；后来 observability 模块加了同名同构的一个。新代码（SkillController / EvalController / WorkflowController 等）都复用 `observability.vo.ApiResponse`。两份长得一样可以无痛合并，但属于"代码清理"工单，不影响功能。

### A.3 为什么 `@DependsOn("skillScanner")`？

```java
@Bean(name = "skillTools")
@DependsOn("skillScanner")
public ToolCallback[] skillTools(SkillRegistry skillRegistry) {
    List<Skill> all = skillRegistry.listAll();
    // ...
}
```

Spring 容器创建 bean 是有先后的。`skillTools` 这个 bean 需要 `SkillRegistry` 里**已经有内容**才能正确工作。但 `SkillRegistry` 本身可以"先创建空的，再被 SkillScanner 填充"。所以加 `@DependsOn` 强制 Spring 在创建 `skillTools` 前先初始化好 `skillScanner`（它的 `@PostConstruct` 会跑完）。

### A.4 一句"为什么这个项目能撑简历"

因为每个模块都对应了 AI Agent 领域的一个**工程方法论**：
- Skill 抽象 → 能力声明式接入
- WorkflowGenerator → LLM-driven orchestration
- WorkflowExecutor → DSL 解释器
- Eval Harness → 可重复评测
- Trace + 装饰器 → 低侵入可观测性
- ReAct + MCP → Agent 框架 + 跨进程工具复用

不是把库拼起来，而是每一块都自己想过"为什么这样设计"。这是简历叙事的核心价值。

---

## 附录 B · 推荐你按顺序读源码的路径（30 分钟入门）

1. `Skill.java` + `AbstractSkill.java` + `PDFGenerationSkill.java`（5 分钟，理解最小能力单元）
2. `SkillScanner.java` + `SkillCallbackAdapter.java`（5 分钟，理解扫描 + 适配）
3. `TraceContextFilter.java` + `TraceContext.java`（5 分钟，理解 trace 怎么传）
4. `LoggingToolCallback.java`（3 分钟，装饰器模板）
5. `BaseAgent.java` + `ReActAgent.java` + `ToolCallAgent.java`（10 分钟，ReAct 全貌）
6. `WorkflowGenerator.java` + `WorkflowExecutor.java`（10 分钟，核心亮点）
7. `EvalService.java` + `KeywordContainsJudge.java` + `LlmAsJudge.java`（10 分钟）
8. `SkillOpenApiController.java` + `DifyDslExporter.java`（5 分钟，对外集成）

读完这 8 步基本能在面试时画出全栈架构图 + 任意点上深挖。
