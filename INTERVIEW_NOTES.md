# VS AI Agent 项目技术总结（面试背诵版）

> 一句话定位：基于 Spring Boot + Spring AI（阿里云百炼 DashScope）构建的 **AI Agent 中台**，
> 集成对话助手、RAG 知识库、工具/技能注册中心、ReAct 智能体、工作流编排（含一句话生成工作流
> 和智能编排两套）、Dify 集成、全链路可观测性。前端 Vue3 + Vite。

---

## 一、技术栈总览

### 后端
- **语言/构建**：Java 21 + Maven，Spring Boot 3.4.4
- **AI 框架**：Spring AI 1.0.0-M6 + Spring AI Alibaba（DashScope 接入），备用 OpenAI 兼容协议（可切换 DeepSeek）
- **向量库**：PostgreSQL + pgvector 插件，Spring AI PgVectorStore，HNSW 索引，1536 维，余弦距离
- **MCP**：Spring AI MCP Client（SSE 方式接入外部 MCP 工具服务器）
- **文档解析**：Apache Tika（PDF/Word/TXT 等多格式解析）、jsoup（网页解析）
- **PDF 生成**：iText（含中文字体 font-asian）
- **持久化对话记忆**：Kryo 序列化 + 本地文件存储
- **接口文档**：Knife4j（OpenAPI 3 + Swagger UI）
- **工具库**：Hutool、Lombok、jsonschema-generator（结构化输出）

### 前端
- Vue 3.5 + Vite 7 + Vue Router 4 + Pinia 3
- Axios 调用 `/api` 前缀接口，SSE（Server-Sent Events）实现流式对话
- marked 渲染 Markdown

---

## 二、整体分层架构

```
Controller (REST) → Service (业务逻辑) → Repository/Client (DB / 外部API)
                ↘ ExecutionTraceRecorder (可观测性，贯穿全链路)
```

包结构（`com.vs.vsaiagent.*`）：

| 包 | 作用 |
|---|---|
| `app` | 业务编排层，`AssistantApp` 是核心，封装六种对话模式 |
| `agent` | ReAct/ToolCall 智能体框架（手写，非 Spring AI 自带） |
| `agentplatform` | 工具/任务注册中心与编排（区别于 skill） |
| `skill` | 技能体系：可被 SKILL.md 描述、可暴露为 OpenAPI 给 Dify 调用 |
| `tools` | 具体工具实现（搜索、抓取、文件、PDF、天文等） |
| `advisor` | Spring AI Advisor 自定义（日志、Re-reading 提示词增强） |
| `chatmemory` | 基于 Kryo 的文件级对话记忆 |
| `rag` | pgvector 向量库配置、文档加载、RAG Advisor 工厂 |
| `knowledgebase` | 文档上传 → 解析 → 分块 → 向量化 → 入库 的 RAG 知识库管理 |
| `workflow` | "一句话生成工作流"（旧）：内存注册、可执行、可评测、可导出 Dify |
| `workflowbuilder` | "智能编排"（新）：生成更贴近 Dify 画布的 IR，重点是导出/导入 Dify DSL |
| `dify` | 对接 Dify 官方 Workflow API + Console API（DSL 导入） |
| `eval` | 评测框架：EvalRunner + EvalJudge，跑测试集判分 |
| `observability` | 全链路 trace：请求日志、阶段日志、TraceContext |
| `config` | 全局配置：LLM Provider 切换、CORS、异步线程池 |

---

## 三、核心数据流（面试重点，建议画图讲）

### 1. 普通对话（AssistantApp.doChat）
```
前端 GET /api/ai/assistant_app/chat/sync?message=xx&chatId=yy
 → AiController
 → AssistantApp.doChat(message, chatId)
    → ExecutionLogService.startRequest()  // 生成 requestId，落库 agent_request_log
    → ChatClient.prompt().user(message)
        .advisors(MessageChatMemoryAdvisor(FileBasedChatMemory), MyLoggerAdvisor)
        .call().chatResponse()            // 调 DashScope LLM
    → ExecutionLogService.logStage(MODEL, ...)  // 落库 agent_stage_log
    → ExecutionLogService.finishSuccess()
 → 返回字符串
```
- **记忆**：`FileBasedChatMemory` 用 Kryo 把 `List<Message>` 序列化到 `{baseDir}/{chatId}.kryo`，
  实现跨请求的多轮上下文（Spring AI `ChatMemory` 接口的自定义实现）。
- **流式**：`doChatByStream` 返回 `Flux<String>`，前端用 SSE 消费，边生成边展示。

### 2. RAG 问答（doChatWithRag）
```
ChatClient.prompt().advisors(
    MessageChatMemoryAdvisor,
    QuestionAnswerAdvisor / RetrievalAugmentationAdvisor(
        VectorStoreDocumentRetriever(topK=3, similarityThreshold=0.5)
    ),
    MyLoggerAdvisor
).call()
```
- 向量库：`PgVectorVectorStoreConfig` 手动配置 `PgVectorStore`（关闭了 Spring AI 自动配置），
  HNSW 索引 + COSINE_DISTANCE + 1536 维。
- 检索阶段单独记一条 `ExecutionStageType.RETRIEVAL` 日志。

### 3. 知识库文档上传（KnowledgeBaseController.upload）
```
POST /api/kb/documents/upload (multipart)
 → KnowledgeBaseService.upload()
    1. DocumentParserService (Tika) 解析文件 → 纯文本
    2. DocumentProcessingService: MyTokenTextSplitter 按 token 切块（带重叠）
       → KnowledgeDocumentEntity(status=PROCESSING) + KnowledgeChunkEntity[] 入库
    3. @Async：逐 chunk 调 EmbeddingModel.embed() → float[1536]
       → 写入 pgvector 的 vector_store 表
       → 文档状态置为 COMPLETED
 → 返回 documentId
```
- **去重**：用 SHA256 计算 `contentHash`，相同文件不重复处理。
- 支持失败重处理 `/reprocess`、索引重建 `/index/rebuild`。

### 4. 工具调用型 Agent（VsManus，ReAct 模式）
```
GET /api/ai/manus/chat → AiController
 → new VsManus(allTools, dashscopeChatModel)
 → BaseAgent.runStream() → SseEmitter
    循环 (最多 maxSteps=30):
      ToolCallAgent.step() = think() + act()
        think(): ChatClient.prompt().tools(availableTools).call()
                 → 解析 LLM 返回的 tool_calls
        act():   ToolCallingManager.executeToolCalls()
                 检测是否调用了 TerminateTool → state = FINISHED
      每步结果按 2~6 字符随机切片，模拟打字机效果通过 SSE 推送
```
- **状态机**：`AgentState { IDLE, RUNNING, FINISHED, ERROR }`
- **设计模式**：模板方法（`BaseAgent.step()` 被 `ReActAgent`/`ToolCallAgent` 覆写）

### 5. Agent Platform 工具执行
```
POST /api/agent-platform/tools/{toolName}/execute
 → AgentPlatformController
 → ToolExecutionServiceImpl.executeByName()
    - InMemoryToolRegistry.findByName(toolName)   // 注册中心，ConcurrentHashMap
    - CompletableFuture.supplyAsync(tool::execute).get(timeoutMs, ...)  // 超时控制
 → 返回 ToolExecuteResult{success, data, costMs, errorMessage}
```
- 工具：`WebSearchTool`（SearchAPI+百度搜索）、`ImageSearchTool`、`WebScrapingTool`(jsoup)、
  `PDFGenerationTool`(iText)、`FileOperationTool`、天文类工具等。
- 所有 `ToolCallback` 都被 `LoggingToolCallback` 包了一层，自动写 `ExecutionStageType.TOOL` 日志。

### 6. 一句话生成工作流（旧，`/workflow/*`）
```
POST /workflow/generate {prompt}
 → WorkflowGenerator.generate(prompt)  // LLM/规则生成 WorkflowDef(nodes,edges)
 → WorkflowRegistry.save()             // 内存注册，可在列表页管理
 → 可执行: POST /workflow/{id}/execute → WorkflowExecutor 按图遍历节点(llm/tool/decision)
 → 可评测: POST /workflow/{id}/eval → 接入 EvalService
 → 可导出: GET /workflow/{id}/dify-dsl → DifyDslExporter 转 YAML
```

### 7. 智能编排（新，`/workflow-builder/*`）
```
POST /workflow-builder/generate {requirement}
 → WorkflowPlanningService.plan()    // LLM 优先 + 规则兜底（app.workflow-builder.planner=llm|rule）
     ├─ LlmWorkflowPlanner: ChatClient 结构化输出 LlmPlan{name, capabilities[], llmInstruction}
     │    LLM 只做"语义决策"（取名 / 选哪些已注册工具技能 / 核心指令），
     │    Java assemble() 确定性拼装 start→[tool...]→llm→answer，保证图合法 + 变量正确；
     │    能力引用与注册表核对，臆造的工具直接丢弃；validateIr 兜底，不过则回退
     └─ 回退 planByRule: 关键词规则（"联网搜索"→tool:web_search、"生成PDF"→skill:pdf-generation）
 → WorkflowDslGenerateService.toDslYaml(ir)   // 生成贴近 Dify 画布结构的 DSL YAML
 → WorkflowDslValidateService.validateDsl()   // GraphValidateUtil 校验DAG无环、节点齐全
 → WorkflowFileService.save()                 // 落盘
 → 响应 ApiResponse.success({id, name, ir, dslYaml, valid, errors})

POST /workflow-builder/run {ir, input}
 → WorkflowBuilderExecutionService 在本地按 IR 顺序跑一遍（不依赖 Dify），给出每步预览

POST /workflow-builder/import/{workflowId}
 → DifyConsoleClient 用账号密码/token 登录 Dify 控制台，把 DSL 一键导入成 Dify App
```
- **与旧模块的关键区别**（你这次实际修复的 bug 背景）：
  - 旧模块所有响应早已统一包装为 `ApiResponse<T>{code,message,data}`，与前端 `unwrap()` 约定一致；
  - 新模块最初直接返回裸 DTO，导致前端 `response.data.code !== 0` 永远成立 → 报"请求失败"；
  - 修复方式：让 `WorkflowBuilderController` 和其 `@RestControllerAdvice`
    （`WorkflowBuilderExceptionHandler`，用 `assignableTypes` 限定作用范围）统一返回
    `ApiResponse.success(...)` / `ApiResponse.fail(...)`，与旧模块模式对齐。
  - 这是一个典型的**前后端契约不一致**问题：HTTP 200 但业务码缺失，前端框架性的
    `unwrap()` 直接把所有"非0 code"当失败抛出，错误信息被吞掉变成统一的"请求失败"。

### 8. 全链路可观测性（贯穿以上所有流程）
```
ExecutionTraceRecorder.start(scene, input, model) → requestId
  ... 业务执行过程中 ...
ExecutionTraceRecorder.stage(requestId, stageType, ...)  // MODEL/TOOL/RETRIEVAL/FUNCTION_CALL
ExecutionTraceRecorder.success(requestId, output) / fail(requestId, error)

GET /observability/requests/{requestId} → RequestTraceVO（请求+所有阶段日志聚合）
```
- `TraceContextFilter`（Servlet Filter）从 `X-Trace-ID` 请求头取/生成 traceId，存入
  `ThreadLocal`（`TraceContext`），便于跨层访问而不用层层传参。
- Recorder 对所有异常做**非致命降级**（吞掉异常只打 warn 日志），避免观测代码影响主流程。

---

## 四、关键框架知识点 & 可深挖的面试点

### Spring AI
- **ChatClient + Advisor 链**：`MessageChatMemoryAdvisor`（多轮记忆）、
  `QuestionAnswerAdvisor`/`RetrievalAugmentationAdvisor`（RAG）、自定义 `MyLoggerAdvisor`
  （实现 `CallAroundAdvisor`/`StreamAroundAdvisor`，order=0）、
  `ReReadingAdvisor`（Re2 技术：把问题在 prompt 里重复一次提升推理质量）。
- **ToolCallback / @Tool**：把 Java 方法暴露成 LLM 可调用的工具；
  `ToolCallingManager` 负责解析 LLM 返回的 tool_calls 并执行；
  `DashScopeChatOptions.proxyToolCalls=true` 表示手动接管工具调用循环（而不是 Spring AI 自动循环），
  这是实现自定义 ReAct 循环（VsManus）的关键开关。
- **结构化输出**：`ChatClient.call().entity(SomeRecord.class)`，配合 jsonschema-generator。
- **MCP（Model Context Protocol）**：通过 `ToolCallbackProvider` 把外部 MCP Server 的工具
  动态注入到 `tools()` 中，实现"工具生态可插拔"。
- **PgVectorStore**：手动 `@Bean` 配置而非自动配置（main class 排除了
  `PgVectorStoreAutoConfiguration`），可以解释为什么——便于自定义表名/维度/索引类型，
  以及避免应用启动时连不上向量库导致整体启动失败的耦合（实际上仍会失败，但可控配置点更清晰）。

### Spring Boot / Web
- `@ConditionalOnProperty` 实现 LLM Provider 运行期可切换（`app.llm.provider=dashscope|deepseek`），
  `@Primary` 解决多 Bean 注入歧义。
- `@RestControllerAdvice(assignableTypes=...)` 实现**模块级**异常处理（不是全局），
  避免不同模块的异常处理策略互相污染。
- 统一响应体 `ApiResponse<T>`（`code/message/data`，`success()`/`fail()` 静态工厂）——
  典型的"前后端契约"封装，前端用一个 `unwrap()` 函数统一拆包/抛错。
- `WebMvcConfigurer` 全局 CORS 配置；`@EnableAsync` + 自定义线程池支撑知识库异步入库。
- Servlet `Filter`（`TraceContextFilter`）+ `ThreadLocal` 做请求级上下文传递。

### 设计模式（按出现位置）
| 模式 | 体现 |
|---|---|
| 模板方法 | `BaseAgent.run()` 固定流程，`step()`/`think()`/`act()` 交给子类 |
| 状态机 | `AgentState`：IDLE→RUNNING→FINISHED/ERROR |
| 注册中心 | `ToolRegistry`/`SkillRegistry` + `InMemory*Registry`（ConcurrentHashMap） |
| 适配器 | `SkillCallbackAdapter`（Skill → ToolCallback）、`LoggingToolCallback`（ToolCallback 包装加日志） |
| 策略 | `EvalRunner`/`EvalJudge` 可插拔；六种 `doChatXxx` 对应不同对话策略 |
| 工厂 | `AssistantAppRagCustomAdvisorFactory`、`LlmProviderConfig` 条件化创建 ChatModel |
| 外观 | `ExecutionTraceRecorder` 包一层 `ExecutionLogService`，吞异常 |
| 建造者 | `SkillMetadata.builder()`、Lombok `@Builder` |

### 数据库设计要点
- `knowledge_document` / `knowledge_chunk` / `vector_store`（pgvector）三表支撑 RAG 全流程；
  `contentHash` 去重，`status` 状态机（PENDING→PROCESSING→COMPLETED/FAILED）。
- `agent_request_log` / `agent_stage_log` 一对多，支撑可观测性查询（按 requestId/sessionId/时间范围）。

---

## 五、常见面试问答要点

**Q: 为什么要自己写一套 Agent 框架（BaseAgent/ReActAgent/ToolCallAgent），而不是完全用 Spring AI 自带的工具调用循环？**
A: Spring AI 默认会自动把"LLM 决定调用工具→执行→把结果喂回 LLM"这个循环跑完才返回；
而要做**逐步可观测、可流式输出中间过程、可在中途按特定工具（TerminateTool）主动终止**的
Agent（如 Manus 风格），需要把这个循环拿到自己手里——通过
`DashScopeChatOptions.proxyToolCalls=true` 关闭自动循环，自己实现 think/act 循环并按状态机管理。

**Q: RAG 检索质量怎么保证/优化的？**
A: 1) 自定义 `MyTokenTextSplitter` 按 token 而非字符切分，减少语义割裂；
2) `MyKeywordEnricher` 给文档块附加关键词元数据辅助检索；
3) `QueryRewriter`/多查询扩展（demo 中有 MultiQueryExpander）；
4) HNSW 索引 + 余弦相似度 + topK/相似度阈值过滤；5) 文档去重避免重复召回。

**Q: 全链路日志怎么落地的，会不会影响主流程性能/可靠性？**
A: `ExecutionTraceRecorder` 作为外观层，所有写日志操作捕获异常仅记录 warn，绝不向上抛出，
保证观测能力是"锦上添花"而非主流程依赖；`TraceContextFilter` 用 ThreadLocal 传递 traceId，
避免每层方法签名都加 requestId 参数。

**Q: 两套工作流（一句话生成 vs 智能编排）为什么并存，怎么取舍？**
A: 旧模块面向"应用内可管理、可评测"的工作流（注册表+执行器+评测集成）；
新模块面向"生成贴近 Dify 画布结构的 IR，一键导出/导入 Dify 标准 DSL，并提供轻量本地预览"，
聚焦"产出可被 Dify 消费的产物"。两者是面向不同消费场景的平行实现，未来可能收敛但目前独立演进。

**Q: 你最近修复的"智能编排请求失败"是什么问题？**
A: 后端 HTTP 200、JSON 数据完全正确，但前端 `unwrap()` 约定所有响应必须是
`{code:0, data, message}` 结构，新模块的 Controller 直接返回裸 DTO，缺 `code` 字段，
导致前端判定为失败并丢弃真实数据，统一显示"请求失败"。修复：让新模块 Controller 和
其 `@RestControllerAdvice` 都包一层 `ApiResponse.success()/fail()`，与老模块对齐。
体现的是**接口契约一致性**和**异常信息透传**的重要性。
