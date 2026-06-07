# 项目模块梳理文档

## 1. 项目整体定位

这是一个基于 `Spring Boot + Spring AI + PostgreSQL/PGVector + Vue 3/Vite` 的 AI 应用平台，当前主要包含以下能力：

- 通用 AI 助手应用：支持普通对话、流式对话、RAG 检索增强、工具调用、MCP 调用
- AI 超级智能体：支持多步思考、工具选择与执行
- 智能体平台：统一暴露工具注册、工具执行、任务编排能力
- 知识库模块：支持文档上传、解析、切块、向量化、重建索引
- 可观测性模块：支持请求链路追踪、阶段日志记录、失败查询
- Web 前端：提供聊天页面和执行日志查询页面
- MCP 子服务：独立提供图片搜索工具能力

从工程角度看，这是一个“AI 应用后端主服务 + 前端界面 + 独立 MCP 工具服务”的多模块项目。

---

## 2. 总体架构

### 2.1 后端主服务

后端主服务启动类是：

- `src/main/java/com/vs/vsaiagent/VsAiAgentApplication.java`

它负责：

- 启动 Spring Boot 容器
- 扫描和装配 Controller、Service、Config、Repository 等 Bean
- 加载 AI 模型、工具、知识库、日志模块等能力

### 2.2 前端服务

前端目录：

- `vs-agent-web/`

它负责：

- 提供首页入口
- 对接 AI 助手 SSE 聊天接口
- 对接 Manus 智能体 SSE 接口
- 对接执行日志查询接口

### 2.3 MCP 子服务

子项目目录：

- `vs-image-search-mcp-server/`

它负责：

- 作为 MCP Server 暴露图片搜索能力
- 被主后端通过 MCP Client 模式调用

---

## 3. 模块划分

### 3.1 基础配置模块

对应目录：

- `src/main/java/com/vs/vsaiagent/config`
- `src/main/resources`

主要职责：

- 配置 Spring Boot 运行参数
- 配置 CORS、异步线程池
- 配置 LLM 模型提供方
- 配置数据库、向量存储、外部 API Key

关键技术：

- Spring Boot 自动配置
- `@Configuration`
- `WebMvcConfigurer`
- YAML 配置文件
- `@Value`、属性注入

典型文件：

- `AsyncConfig.java`：异步执行线程池配置
- `CorsConfig.java`：跨域配置
- `LlmProviderConfig.java`：大模型客户端配置
- `application-xxxxx.yml`：项目环境配置

数据流：

1. Spring Boot 启动
2. 加载 `application-xxxxx.yml`
3. 创建配置类 Bean
4. 其他模块通过依赖注入获取配置后的组件

---

### 3.2 通用 AI 助手应用模块

对应目录：

- `src/main/java/com/vs/vsaiagent/app`
- `src/main/java/com/vs/vsaiagent/controller`
- `src/main/java/com/vs/vsaiagent/chatmemory`

主要职责：

- 提供普通对话能力
- 提供流式 SSE 对话能力
- 提供 RAG 检索增强问答
- 提供工具调用能力
- 提供 MCP 调用能力

关键类：

- `AssistantApp.java`
- `AiController.java`
- `FileBasedChatMemory.java`

关键技术：

- Spring MVC / REST
- Spring AI `ChatClient`
- SSE：`Flux<String>`、`SseEmitter`
- Chat Memory 会话记忆
- Prompt Engineering
- Tool Calling
- MCP Client

#### 数据流：普通聊天

1. 前端调用 `/ai/assistant_app/chat/sync` 或 `/ai/assistant_app/chat/sse`
2. `AiController` 接收请求
3. 调用 `AssistantApp.doChat()` 或 `AssistantApp.doChatByStream()`
4. `AssistantApp` 基于 `ChatClient` 拼装 system prompt、user prompt、chat memory
5. 大模型生成回复
6. 返回同步文本或 SSE 流

#### 数据流：RAG 对话

1. 前端调用 `/ai/assistant_app/chat_rag/sse`
2. `AiController` 转发到 `AssistantApp.doChatWithRagSse()`
3. `AssistantApp` 先调用 `VectorStore.similaritySearch()`
4. 得到召回文档摘要
5. 通过 `QuestionAnswerAdvisor` 将知识注入模型上下文
6. 模型生成最终回答
7. 流式返回前端

#### 数据流：工具调用对话

1. 用户输入进入 `AssistantApp.doChatWithTools()`
2. `ChatClient.tools(allTools)` 将工具列表注入模型
3. 模型按需触发工具调用
4. 工具执行结果返回模型
5. 模型基于工具结果生成最终回答

#### 数据流：MCP 对话

1. 用户输入进入 `AssistantApp.doChatWithMcp()`
2. `toolCallbackProvider` 暴露 MCP 工具能力
3. 模型调用 MCP 工具
4. 工具结果返回模型
5. 模型生成最终回答

工程特点：

- `AssistantApp` 是一个典型的应用服务类，聚合对话、RAG、工具、日志能力
- 通过 `ChatMemory` 保持会话上下文
- 通过 `MyLoggerAdvisor`、`QuestionAnswerAdvisor` 等实现扩展

---

### 3.3 智能体模块

对应目录：

- `src/main/java/com/vs/vsaiagent/agent`

主要职责：

- 定义智能体抽象
- 支持多步推理
- 支持工具选择与多轮执行

关键类：

- `BaseAgent.java`
- `ReActAgent.java`
- `ToolCallAgent.java`
- `VsManus.java`
- `agent/model/AgentState.java`

关键技术：

- 面向对象抽象
- ReAct 模式
- 多步 Agent Loop
- Tool Calling
- Prompt 模板设计

#### 数据流：Manus 智能体

1. 前端调用 `/ai/manus/chat`
2. `AiController` 创建 `VsManus`
3. `VsManus` 继承 `ToolCallAgent`
4. 智能体使用 system prompt 与 next-step prompt 决定下一步动作
5. 若需要工具，则调用注册工具
6. 每一步结果继续反馈给模型
7. 直到完成任务或调用 terminate 工具终止

工程特点：

- `VsManus` 是智能体的“业务人格”和执行策略定义
- `ToolCallAgent` 负责工具型智能体的通用执行框架
- 适合承载复杂任务、多工具协作、半自动编排场景

---

### 3.4 工具系统模块

对应目录：

- `src/main/java/com/vs/vsaiagent/tools`
- `src/main/java/com/vs/vsaiagent/agentplatform/tool`

主要职责：

- 封装外部能力为模型可调用工具
- 统一注册工具
- 供 AssistantApp、Manus、Agent Platform 复用

已有工具：

- 文件操作
- Web 搜索
- 网页抓取
- 图片搜索
- 资源下载
- 终端执行
- PDF 生成
- terminate 终止工具

关键类：

- `ToolRegistration.java`
- `FileOperationTool.java`
- `WebSearchTool.java`
- `WebScrapingTool.java`
- `ImageSearchTool.java`
- `ResourceDownloadTool.java`
- `TerminalOperationTool.java`
- `PDFGenerationTool.java`
- `TerminateTool.java`

关键技术：

- Spring AI `ToolCallback`
- 工具注册与统一注入
- 外部 API 调用
- 文件 IO
- 进程调用
- 网页解析 `Jsoup`
- PDF 生成 `iText`

#### 数据流：工具调用

1. Spring 启动时 `ToolRegistration` 创建 `ToolCallback[]`
2. 各工具被包装成标准 ToolCallback
3. AssistantApp 或 VsManus 将工具注入 `ChatClient`
4. 模型在推理过程中发起函数调用
5. 具体工具执行逻辑返回字符串结果
6. 结果回注给模型形成最终答案

工程特点：

- 使用统一注册入口，避免工具分散管理
- 通过装饰器可以给所有工具增加日志、限流、权限等横切能力

---

### 3.5 智能体平台模块

对应目录：

- `src/main/java/com/vs/vsaiagent/agentplatform`

主要职责：

- 提供“平台级”的工具管理和任务执行 API
- 对工具执行做统一封装
- 对复杂任务做编排

子层次说明：

- `controller`：对外提供平台接口
- `dto/model/vo`：平台入参与出参对象
- `registry`：工具注册表
- `service`：任务编排、工具执行桥接服务
- `tool`：平台层工具抽象

关键类：

- `AgentPlatformController.java`
- `ToolRegistry.java`
- `InMemoryToolRegistry.java`
- `ToolRegistryInitializer.java`
- `ToolExecutionService.java`
- `TaskOrchestratorService.java`
- `ReActBridgeService.java`

关键技术：

- REST API
- 注册中心模式
- 编排服务模式
- DTO/VO 分层
- 工具标签路由

#### 数据流：按工具名称执行

1. 客户端调用 `/agent-platform/tools/{toolName}/execute`
2. `AgentPlatformController` 接收请求
3. `ToolExecutionService` 根据名称查找工具
4. 组装 `ToolExecuteRequest`
5. 工具执行并返回 `ToolExecuteResult`

#### 数据流：按任务执行

1. 客户端调用 `/agent-platform/tasks/execute`
2. `TaskOrchestratorService` 接收任务请求
3. 根据任务上下文和步骤定义进行编排
4. 如有需要，调用工具执行服务或 ReAct Bridge
5. 汇总所有步骤结果并返回

工程特点：

- 这是项目中“平台化”最明显的一层
- 后续可以扩展为多租户、多工具来源、任务模板化系统

---

### 3.6 RAG 检索增强模块

对应目录：

- `src/main/java/com/vs/vsaiagent/rag`

主要职责：

- 构建通用助手的知识检索增强能力
- 负责向量存储配置、文档加载、查询重写、切块策略

关键类：

- `PgVectorVectorStoreConfig.java`
- `AssistantAppVectorStoreConfig.java`
- `AssistantAppDocumentLoader.java`
- `QueryRewriter.java`
- `MyTokenTextSplitter.java`
- `MyKeywordEnricher.java`
- `AssistantAppRagCloudAdvisorConfig.java`
- `AssistantAppRagCustomAdvisorFactory.java`

关键技术：

- RAG
- Vector Store / PGVector
- 查询重写
- 文档切块
- 检索增强 Prompt 注入

#### 数据流：知识检索

1. 用户输入问题
2. 可先通过 `QueryRewriter` 改写查询
3. `VectorStore` 执行相似度检索
4. 得到若干 `Document`
5. 文档内容以 advisor 方式注入模型上下文
6. 模型基于外部知识生成回答

工程特点：

- 将 RAG 做成单独目录，便于替换检索策略
- 手动配置 PGVector，灵活性高于纯自动装配

---

### 3.7 知识库模块

对应目录：

- `src/main/java/com/vs/vsaiagent/knowledgebase`

主要职责：

- 管理知识文档
- 执行文档上传、解析、切块、向量化、状态查询
- 支撑 RAG 模块的数据来源

子目录说明：

- `config`：知识库表初始化
- `controller`：上传、删除、重处理、索引重建接口
- `dto/entity/enums/vo`：知识库数据模型
- `repository`：文档、切片、向量存储访问
- `service`：知识库核心业务
- `util`：哈希工具

关键类：

- `KnowledgeBaseController.java`
- `KnowledgeBaseService.java`
- `DocumentParserService.java`
- `DocumentProcessingService.java`
- `KnowledgeDocumentRepository.java`
- `KnowledgeChunkRepository.java`
- `KnowledgeVectorRepository.java`

关键技术：

- Multipart 文件上传
- 文档解析 `Apache Tika`
- 文本切块
- 向量嵌入
- PostgreSQL/PGVector
- JdbcTemplate

#### 数据流：文档入库

1. 前端上传文件到 `/kb/documents/upload`
2. `KnowledgeBaseController` 收到 `MultipartFile`
3. `KnowledgeBaseService` 记录文档元信息
4. `DocumentParserService` 提取文本
5. `DocumentProcessingService` 做切块
6. 通过向量库写入 embedding
7. 更新文档处理状态

#### 数据流：重建索引

1. 调用 `/kb/documents/index/rebuild`
2. `KnowledgeBaseService` 遍历知识数据
3. 重新计算向量或重建索引
4. 后续 RAG 查询可基于新索引检索

工程特点：

- 知识库设计相对完整，已经具备独立子系统雏形
- 与 RAG 模块形成“数据层 + 检索层”的配合关系

---

### 3.8 可观测性与执行日志模块

对应目录：

- `src/main/java/com/vs/vsaiagent/observability`

主要职责：

- 记录一次请求的完整执行链路
- 记录检索、工具、模型生成、输出、错误等阶段日志
- 提供按请求、会话、失败时间范围的查询能力

子目录说明：

- `config`：建表和链路上下文注入
- `context`：trace/request/session 上下文
- `controller`：日志查询接口
- `dto/entity/enums/vo`：日志数据模型
- `repository`：日志数据访问
- `service`：统一日志服务
- `tool`：工具调用日志包装器

关键类：

- `TraceContextFilter.java`
- `TraceContext.java`
- `ExecutionLogService.java`
- `ExecutionLogServiceImpl.java`
- `LoggingToolCallback.java`
- `ObservabilityController.java`

关键技术：

- `OncePerRequestFilter`
- `ThreadLocal`
- 统一日志服务
- 装饰器模式
- JdbcTemplate
- 低侵入可观测性接入

#### 数据流：请求追踪

1. HTTP 请求进入系统
2. `TraceContextFilter` 从 Header 读取或自动生成 `traceId/requestId/sessionId`
3. 把链路信息写入 `TraceContext`
4. 后续 AssistantApp、工具调用、日志服务均可读取当前上下文

#### 数据流：执行日志记录

1. 业务方法开始时调用 `ExecutionLogService.startRequest()`
2. 写入请求主表，并记录 INPUT 阶段
3. 在检索、模型生成、工具执行等阶段调用 `logStage()`
4. 请求结束时调用 `finishSuccess()` 或 `finishFail()`
5. 最终形成一条请求主记录 + 多条阶段明细记录

#### 数据流：工具日志记录

1. `ToolRegistration` 创建工具列表
2. 每个工具被 `LoggingToolCallback` 包装
3. 工具执行前后记录工具入参、出参、耗时、成功状态
4. 数据写入阶段日志表

工程特点：

- 这是标准的“横切关注点”模块
- 通过服务封装、过滤器、装饰器三层方式做到低侵入
- 很适合继续扩展统计分析面板

---

### 3.9 前端模块

对应目录：

- `vs-agent-web/src`

主要职责：

- 提供用户交互界面
- 管理聊天会话状态
- 通过 HTTP/SSE 调用后端
- 展示聊天结果和执行日志结果

关键目录：

- `components`：通用组件
- `router`：路由
- `services`：API 封装
- `stores`：状态管理
- `views`：页面

关键页面：

- `Home.vue`：首页入口
- `AssistantApp.vue`：通用助手页面
- `ManusApp.vue`：超级智能体页面
- `Observability.vue`：执行日志查询页面

关键技术：

- Vue 3 Composition API
- Vue Router
- Pinia
- Axios
- EventSource / SSE
- Vite

#### 数据流：前端聊天

1. 用户在聊天输入框输入内容
2. 页面通过 `services/api.js` 建立 EventSource SSE 连接
3. 后端流式返回内容块
4. 页面逐步拼接 AI 回复
5. `store/chat.js` 管理前端消息状态

#### 数据流：前端日志查询

1. 用户进入 `Observability.vue`
2. 输入 requestId / sessionId / 时间范围
3. 前端通过 Axios 调用 `/observability/*`
4. 后端返回统一 `ApiResponse`
5. 页面展示请求链路、会话记录、失败记录

工程特点：

- 聊天和日志查询两类场景分离明确
- SSE 使用方式清晰，便于学习前后端实时流式通信

---

### 3.10 MCP 图片搜索子服务

对应目录：

- `vs-image-search-mcp-server`

主要职责：

- 独立运行图片搜索工具服务
- 提供给主系统通过 MCP 协议调用

关键技术：

- Spring Boot
- MCP Server
- SSE / stdio 双运行模式

关键文件：

- `VsImageSearchMcpServerApplication.java`
- `tools/ImageSearchTool.java`
- `application-sse.yml`
- `application-stdio.yml`

数据流：

1. MCP 子服务启动
2. 注册图片搜索工具
3. 主系统通过 MCP Client 连接它
4. 模型触发工具调用时，实际请求发往 MCP 子服务
5. 返回图片搜索结果给主系统，再回注模型

---

## 4. 核心调用链总览

### 4.1 一次普通聊天请求

1. 前端 `AssistantApp.vue` 发起请求
2. `AiController` 接收请求
3. `AssistantApp` 调用 `ChatClient`
4. Chat Memory 注入历史消息
5. 模型生成结果
6. 可观测性模块记录输入、模型、输出
7. 返回给前端

### 4.2 一次 RAG 对话请求

1. 前端发起 `chat_rag` 请求
2. `AssistantApp` 先查 `VectorStore`
3. 召回相关文档
4. 文档通过 advisor 注入 Prompt
5. 模型结合知识回答
6. 日志模块记录 RETRIEVAL 和 MODEL 阶段
7. 返回结果

### 4.3 一次工具调用请求

1. 用户输入到模型
2. 模型选择工具
3. `ToolCallback` 执行对应工具
4. `LoggingToolCallback` 记录工具调用细节
5. 工具结果交回模型
6. 模型生成最终答案

### 4.4 一次知识库文档上传

1. 用户上传文件
2. 知识库模块解析文本
3. 文本切块
4. 生成向量并入库
5. 文档状态更新
6. 后续被 RAG 模块检索使用

### 4.5 一次执行日志查询

1. 前端进入执行日志页面
2. 调用 `ObservabilityController`
3. `ExecutionLogService` 查询主表和阶段表
4. 返回请求链路或失败记录
5. 前端展示结果

---

## 5. 数据存储设计概览

项目目前主要依赖 PostgreSQL，典型数据包括：

- 会话与聊天上下文：文件记忆或内存记忆
- 知识库文档元信息
- 知识切片数据
- 向量索引数据
- 请求执行主日志
- 请求阶段明细日志

使用技术：

- PostgreSQL
- PGVector
- JdbcTemplate

设计特点：

- 不强依赖 JPA/Hibernate
- 更偏向手写 SQL 和轻量级 Repository
- 对 AI 场景中的“日志、切片、向量数据”更灵活

---

## 6. 项目用到的主要技术栈

### 6.1 Java 后端

- Java 21
- Spring Boot 3
- Spring MVC
- Spring AI
- Reactor `Flux`
- Lombok
- JdbcTemplate
- Maven

### 6.2 AI 能力

- ChatClient
- Prompt Engineering
- Chat Memory
- Tool Calling
- ReAct Agent
- RAG
- PGVector
- MCP

### 6.3 前端

- Vue 3
- Vite
- Vue Router
- Pinia
- Axios
- SSE / EventSource

### 6.4 其他库

- Hutool
- Jsoup
- Apache Tika
- iText PDF
- LangChain4j（示例）

---

## 7. 推荐阅读顺序

如果你想从“读懂项目”切入，建议按这个顺序看：

### 第 1 步：看启动和配置

- `pom.xml`
- `VsAiAgentApplication.java`
- `application-xxxxx.yml`
- `config/*`

### 第 2 步：看主业务入口

- `controller/AiController.java`
- `app/AssistantApp.java`

### 第 3 步：看工具与智能体

- `tools/*`
- `agent/*`
- `agentplatform/*`

### 第 4 步：看 RAG 和知识库

- `rag/*`
- `knowledgebase/*`

### 第 5 步：看可观测性

- `observability/*`

### 第 6 步：看前端联调

- `vs-agent-web/src/services/api.js`
- `vs-agent-web/src/views/*.vue`

---

## 8. 一句话总结

这个项目不是单纯的“聊天 Demo”，而是一个围绕 AI 对话、RAG、工具调用、任务编排、知识库和可观测性构建的综合型智能体平台，已经具备继续演进为生产级 AI 应用平台的基础结构。
