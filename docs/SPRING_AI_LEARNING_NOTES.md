# Spring AI 知识点讲义（结合 vs-ai-agent 项目）

> 面向「会 Spring Boot、但不熟 Spring AI」的同学。
> 全程结合本项目 `com.vs.vsaiagent` 的真实代码，按「**从底层模型 → 到上层应用**」的顺序串讲。
> 环境：Spring AI `1.0.0-M6` + Spring AI Alibaba（DashScope）+ Spring Boot 3.4.4 / JDK 21。

---

## 0. 先建立全局心智模型

Spring AI 的核心是一组**可替换的抽象接口**，本项目几乎都用到了：

| 抽象 | 作用 | 项目里的体现 |
|---|---|---|
| `ChatModel` | 最底层，直连大模型 | `dashscopeChatModel`、`openAiChatModel` |
| `ChatClient` | 流式 API 门面（推荐入口） | `AssistantApp` 里的 `chatClient` |
| `Advisor` | 拦截器 / AOP，包裹每次调用 | `MyLoggerAdvisor`、`ReReadingAdvisor` |
| `ChatMemory` | 对话记忆 | `FileBasedChatMemory` |
| `ToolCallback` | 工具（Function Calling） | `WebSearchTool` + `ToolRegistration` |
| `ToolCallingManager` | 手动驱动工具调用 | `ToolCallAgent` |
| `VectorStore` / `EmbeddingModel` | RAG 向量检索 | `PgVectorVectorStoreConfig` |
| MCP Client / Server | 跨进程工具协议 | `doChatWithMcp` |

一句话：**`ChatModel` 是发动机，`ChatClient` 是方向盘，`Advisor` 是行车记录仪 + 辅助驾驶，其余都是挂载件。**

---

## 1. ChatModel：最底层的模型抽象

`ChatModel` 是 Spring AI 对「一个能聊天的大模型」的统一接口。你**不直接 new 它**，而是由 starter 自动装配。本项目 `pom.xml` 引入了三个 provider：

- `spring-ai-alibaba-starter` → 自动产出 `dashscopeChatModel`
- `spring-ai-openai-spring-boot-starter` → 自动产出 `openAiChatModel`（项目里用它接 DeepSeek）

**知识点：多模型如何共存？** 看 `config/LlmProviderConfig.java`：

```java
@Bean(name = "primaryDashscopeChatModel")
@Primary                                    // ① 同类型多个 Bean 时的默认首选
@ConditionalOnProperty(name = "app.llm.provider",
        havingValue = "dashscope", matchIfMissing = true)  // ② 按配置开关
public ChatModel primaryDashscopeChatModel(
        @Qualifier("dashscopeChatModel") ChatModel dashscopeChatModel) {  // ③ 按名字精确注入
    return dashscopeChatModel;
}
```

这里同时用了三个 Spring 核心机制来解决「**有多个 `ChatModel` Bean，该注入哪个**」的歧义：

- `@Qualifier("name")`：同类型多个 Bean，按 **Bean 名字**精确点名；
- `@Primary`：不写 `@Qualifier` 时，谁是默认；
- `@ConditionalOnProperty`：根据 `application.yml` 里 `app.llm.provider` 的值，**编译期不变、启动期决定**装配哪个，实现「改一行配置切换底层大模型」。

> 这是 Spring AI「面向接口编程」的最大价值：`AssistantApp` 全程只依赖 `ChatModel` 接口，换 DashScope / DeepSeek / OpenAI 上层代码一行不改。

---

## 2. ChatClient：推荐的高层入口（Fluent API）

`ChatModel` 能用但太裸。`ChatClient` 是它的**流式封装**，本项目所有业务对话都走它。构建一次、复用多次 —— 看 `app/AssistantApp.java` 构造函数：

```java
chatClient = ChatClient.builder(dashscopeChatModel)
        .defaultSystem(SYSTEM_PROMPT)            // 默认系统提示词
        .defaultAdvisors(                        // 默认挂载的拦截器
                new MessageChatMemoryAdvisor(chatMemory),
                new MyLoggerAdvisor()
        )
        .build();
```

`default*` 表示「每次调用都带上」的基线配置。单次调用时可以再追加 / 覆盖。**一次完整调用的链式结构**（`doChat`）：

```java
ChatResponse resp = chatClient
    .prompt()                                    // 开始构建一次请求
    .user(message)                               // 用户输入
    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)  // 给 advisor 传参
                          .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
    .call()                                      // 同步执行（阻塞）
    .chatResponse();                             // 取完整响应对象
String content = resp.getResult().getOutput().getText();   // 一路取到文本
```

**几个必须掌握的「收尾」方法（终结操作）：**

| 写法 | 返回 | 项目位置 |
|---|---|---|
| `.call().chatResponse()` | 完整 `ChatResponse`（含 token、元数据） | `doChat` |
| `.call().content()` | 直接给 `String` | 多处 |
| `.call().entity(Xxx.class)` | **结构化对象** | `doChatWithReport` |
| `.stream().content()` | `Flux<String>` 流式 | `doChatByStream` |

---

## 3. 结构化输出：`.entity()` —— 让大模型返回 Java 对象

这是面试高频点。看 `doChatWithReport`：

```java
record ConversationReport(String title, List<String> suggestions) {}

ConversationReport report = chatClient.prompt()
        .system(SYSTEM_PROMPT + "...输出报告，title=..., suggestions=...")
        .user(message)
        .call()
        .entity(ConversationReport.class);    // ← 关键
```

**原理**：`.entity()` 背后是 `StructuredOutputConverter`。Spring AI 会用 `victools/jsonschema-generator`（pom 里专门引了）把 `ConversationReport` 这个 record **反射生成 JSON Schema**，自动塞进提示词告诉模型「请按这个格式返回 JSON」，拿到回复后再反序列化成对象。你不用手写「请返回 JSON {...}」也不用手动 parse。

---

## 4. Advisor：Spring AI 的 AOP 拦截链

`Advisor` 是 Spring AI 最有特色的设计 —— 类似 Spring MVC 的拦截器 / AOP 环绕通知，**包裹每一次模型调用**，可在调用前改请求、调用后改响应。

项目里两个自定义 Advisor 是绝佳教材：

**(a) `advisor/MyLoggerAdvisor.java` —— 环绕日志：**

```java
public class MyLoggerAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {
    public AdvisedResponse aroundCall(AdvisedRequest req, CallAroundAdvisorChain chain) {
        req = before(req);                           // 调用前：打印请求
        AdvisedResponse resp = chain.nextAroundCall(req);  // 放行到下一个 advisor / 最终模型
        observeAfter(resp);                          // 调用后：打印响应
        return resp;
    }
}
```

**(b) `advisor/ReReadingAdvisor.java` —— 调用前改写 Prompt（Re2 技巧）：**

```java
return AdvisedRequest.from(advisedRequest)
    .userText("""
        {re2_input_query}
        Read the question again: {re2_input_query}""")   // 把问题重复一遍，提升推理
    .userParams(advisedUserParams)
    .build();
```

**必须理解的 4 个要点：**

1. 接口分两套：`CallAroundAdvisor`（同步）+ `StreamAroundAdvisor`（流式），通常一起实现。
2. `chain.nextAroundCall(req)` = 「放行」，不调用就短路了 —— 这是责任链模式。
3. `getOrder()` 决定执行顺序（数字小先执行）。
4. **传参机制**：业务层 `.advisors(spec -> spec.param(KEY, value))` 注入的参数，Advisor 内部通过 `advisedRequest` 读取 —— 这就是 `MessageChatMemoryAdvisor` 怎么知道 `chatId` 的（见下一节）。

> 流式细节：`MyLoggerAdvisor.aroundStream` 用 `new MessageAggregator().aggregateAdvisedResponse(...)` 把一串碎片 `Flux` 聚合成完整响应再打日志 —— 因为流式响应是一片片来的，要聚合后才能完整记录。

---

## 5. ChatMemory：对话记忆

大模型本身**无状态**，多轮对话靠每次把历史消息一起发过去。`ChatMemory` 接口负责存取历史。

**框架内置 `MessageChatMemoryAdvisor`** 把记忆做成了一个 Advisor（在 `AssistantApp` 的 `defaultAdvisors` 里挂着）。工作流：调用前从 memory 取出最近 N 条历史拼进 prompt，调用后把新一轮问答写回 memory。

业务层怎么指定「哪个会话」「取几条」？就是上面说的 advisor 传参：

```java
.advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)   // 会话隔离
                      .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))         // 取最近 10 条
```

**本项目自定义了存储后端** `chatmemory/FileBasedChatMemory.java`（实现 `ChatMemory` 接口的 `add/get/clear`），用 **Kryo 序列化**把每个会话存成 `{conversationId}.kryo` 文件，做到重启不丢记忆。这里展示了 Spring AI 的另一个扩展点：**记忆后端可插拔**（内置有内存版 `InMemoryChatMemory`，本项目换成了文件版）。

---

## 6. Tool / Function Calling：让模型调用你的 Java 方法

这是 Agent 的核心能力。Spring AI 提供两种风格，本项目**两种都用了**：

**(a) 声明式 `@Tool` 注解** —— 看 `tools/WebSearchTool.java`：

```java
@Tool(description = "Search for information from Baidu Search Engine")
public String searchWeb(
        @ToolParam(description = "Search query keyword") String query) { ... }
```

`@Tool` 的 `description` 极其关键 —— 模型**靠它判断什么时候调这个工具**。`@ToolParam` 描述参数，Spring AI 自动据此生成工具的 JSON Schema 交给模型。

**(b) 集中注册成 `ToolCallback[]`** —— 看 `tools/ToolRegistration.java`：

```java
@Bean
public ToolCallback[] allTools() {
    ToolCallback[] callbacks = ToolCallbacks.from(   // 把多个 @Tool 对象转成统一回调
            fileOperationTool, webSearchTool, ... );
    // 再用装饰器包一层日志 / 埋点
    wrapped[i] = new LoggingToolCallback(callbacks[i], executionLogService);
    return wrapped;
}
```

`ToolCallbacks.from(...)` 把任意带 `@Tool` 的普通对象，扫描转换成框架统一的 `ToolCallback`。然后业务层一行绑定：

```java
chatClient.prompt().user(message).tools(allTools).call()...   // doChatWithTools
```

> 注意 `LoggingToolCallback` 是**装饰器模式**：包住原始 `ToolCallback`，在工具执行前后记埋点，而不改工具本身。这是给框架对象加横切逻辑的常用手法。

---

## 7. 自动 vs 手动工具调用：`ToolCallingManager`（进阶，Agent 必看）

默认情况下，`.tools(allTools).call()` 时 **Spring AI 会自动跑完整个「模型选工具 → 框架执行工具 → 把结果回灌模型 → 再次生成」的循环**，你拿到的是最终答案。

但本项目要做 **ReAct Agent（自己控制 think/act 每一步）**，所以在 `agent/ToolCallAgent.java` 里**关掉了自动调用，改手动驱动**：

```java
this.chatOptions = DashScopeChatOptions.builder()
        .withProxyToolCalls(true)        // ★ 关键：告诉 Spring AI「别自动执行工具，把工具调用原样还给我」
        .build();
this.toolCallingManager = ToolCallingManager.builder().build();
```

于是循环被拆成两半：

```java
// think()：只让模型「决定调哪些工具」，不执行
ChatResponse resp = chatClient.prompt(prompt).tools(availableTools).call().chatResponse();
List<AssistantMessage.ToolCall> toolCalls = resp.getResult().getOutput().getToolCalls();

// act()：手动执行工具，并把结果接回消息上下文
ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
setMessageList(result.conversationHistory());   // 含 assistant 消息 + 工具返回
```

**这是理解 Agent 的关键分水岭：**

- 普通对话 → 用 `ChatClient` + 自动工具调用（`AssistantApp.doChatWithTools`）；
- 自主 Agent → 用 `withProxyToolCalls(true)` + `ToolCallingManager` 手动控制每一步（`ToolCallAgent`），配合 `BaseAgent` 的 `run()` 循环（`maxSteps`、`AgentState` 状态机、`TerminateTool` 终止）实现 think→act→think→act…

`BaseAgent.run()` 就是那个 ReAct 主循环：维护 `messageList` 当上下文、`for (i < maxSteps)` 逐步执行、命中 `doTerminate` 工具或超步数就置 `FINISHED`。

---

## 8. RAG：向量检索增强

RAG 三件套：`EmbeddingModel`（文本 → 向量）+ `VectorStore`（存/查向量）+ 检索拼接。

**向量库配置** `rag/PgVectorVectorStoreConfig.java`：

```java
@Bean
public VectorStore pgVectorVectorStore(JdbcTemplate jdbc, EmbeddingModel dashscopeEmbeddingModel) {
    return PgVectorStore.builder(jdbc, dashscopeEmbeddingModel)
            .dimensions(1536)               // 向量维度，要和 embedding 模型匹配
            .distanceType(COSINE_DISTANCE)  // 相似度算法：余弦距离
            .indexType(HNSW)                // 近邻索引算法
            .initializeSchema(true)         // 自动建表
            .build();
}
```

**检索 + 拼接**（`AssistantApp.doChatWithRag`）。本项目刻意**手动做 RAG** 而没用框架的 `QuestionAnswerAdvisor`：

```java
List<Document> recalls = pgVectorVectorStore.similaritySearch(
        SearchRequest.builder().query(message).topK(4).build());   // ① 检索 top-4
String ctx = buildRagContext(recalls);                              // ② 拼成上下文
chatClient.prompt().system(SYSTEM_PROMPT + ctx).user(message)...    // ③ 塞进 system 再问
```

代码注释也写明了原因：「复用这一次检索结果，避免 `QuestionAnswerAdvisor` 再查一遍向量库」。**知识点对比：**

- `QuestionAnswerAdvisor`（框架自带）= 把「检索 + 拼接」做成 Advisor，一行接入但黑盒；
- 手动检索 = 啰嗦但**可观测**（能把召回数量 / 摘要写进 `executionLogService` 埋点）、可控（检索结果还能复用）。

> `@ConditionalOnBean(EmbeddingModel.class)` + `@Autowired(required=false)`：整套 RAG 是**可选装配**的 —— 没配 embedding 模型时，`pgVectorVectorStore` 为 null，`doChatWithRag` 直接降级回 `doChat`。这是优雅降级的范式。

---

## 9. 流式输出：Reactor `Flux`

`.stream().content()` 返回 `Flux<String>`（响应式流，SSE 推前端）。看 `doChatByStream` 的埋点技巧：

```java
Flux<String> content = chatClient.prompt().user(message)...
        .stream().content()
        .doOnNext(chunks::add)          // 每来一片就收集
        .doOnComplete(() -> { ... })    // 全部结束：拼完整文本 + 记日志
        .doOnError(e -> ...);           // 出错回调
```

`doOnNext/doOnComplete/doOnError` 是 Reactor 的「副作用钩子」，**不改变流本身**，只在数据流过时顺便做埋点 —— 既保证流式实时性，又能在结束时记录完整结果。

---

## 10. MCP：把工具能力跨进程化

MCP（Model Context Protocol）是让工具能**跨进程 / 跨语言**被调用的标准协议。本项目同时是 MCP **客户端**和**服务端**（pom 里两个 starter 都引了）：

- **MCP Client**（`doChatWithMcp`）：把外部 MCP server 暴露的工具当本地工具用：

  ```java
  @Resource(name = "toolCallbacks")          // 注入 MCP client 聚合的工具
  private ToolCallbackProvider toolCallbackProvider;
  ...
  chatClient.prompt().user(message).tools(toolCallbackProvider).call()...
  ```

  注意这里用 `@Resource(name="toolCallbacks")` 精确点名 —— 因为本项目**同时是 MCP server**，容器里有多个 `ToolCallbackProvider`，不点名会歧义（代码注释专门说明了这点）。

- **MCP Server**（`config/McpCapabilityServerConfig.java`）：把自己注册的工具 / 技能反向暴露成 MCP tools，供 Dify 等外部平台动态拉取。

**心智**：`ToolCallback` 是进程内的工具，MCP 是把工具「联网」的协议层，但对 `ChatClient` 而言两者都通过 `.tools(...)` 统一接入 —— 又一次体现「面向统一抽象」。

---

## 复习路线图（建议精读顺序 + 为什么这么排）

这条路线是按「**先打地基、再盖楼，难点放中段**」设计的。每一步都建立在前一步的概念之上，跳着学容易卡住。

### 第 1 站 · `config/LlmProviderConfig.java` —— 模型怎么进容器
- **学什么**：`ChatModel` 抽象、`@Qualifier / @Primary / @ConditionalOnProperty` 三件套、多 provider 切换。
- **为什么放第一**：整个框架都建立在「依赖 `ChatModel` 接口而非具体实现」之上。先理解这一点，后面才会明白为什么 `AssistantApp` 不关心底层是 DashScope 还是 DeepSeek。这是**地基**。

### 第 2 站 · `app/AssistantApp.java`（构造 + `doChat`）—— 一次看全主干
- **学什么**：`ChatClient.builder()` 装配、`.prompt().user().call().chatResponse()` 调用链、`defaultSystem / defaultAdvisors`。
- **为什么放这**：这一个类把 ChatClient + Advisor + Memory + Tool + RAG + MCP 全串起来了，是整个项目的「**总目录**」。先看主干，建立全貌，再逐个钻分支。建议只精读构造函数和 `doChat`，其余方法略读。

### 第 3 站 · `advisor/MyLoggerAdvisor` + `ReReadingAdvisor` —— 拦截链
- **学什么**：`CallAroundAdvisor / StreamAroundAdvisor`、`chain.nextAroundCall()` 放行、调用前改 prompt / 调用后改 response。
- **为什么放这**：Advisor 是 Spring AI 区别于「裸调 SDK」的灵魂。理解它之后，你会突然看懂 Memory、RAG 的 `QuestionAnswerAdvisor` 本质都是同一套拦截器机制 —— **一通百通**。

### 第 4 站 · `tools/WebSearchTool` + `tools/ToolRegistration` —— 工具基础
- **学什么**：`@Tool / @ToolParam` 声明、`ToolCallbacks.from()` 批量转换、`LoggingToolCallback` 装饰器埋点。
- **为什么放这**：工具是 Agent 的「手脚」。先掌握**最简单的自动调用形态**（声明工具 → `.tools()` 绑定 → 框架自动跑完），为第 5 站的「手动控制」做对比铺垫。

### 第 5 站 · `agent/ToolCallAgent` + `agent/BaseAgent` —— 全项目难点 ⭐
- **学什么**：`withProxyToolCalls(true)` 关闭自动调用、`ToolCallingManager.executeToolCalls()` 手动执行、`think()/act()` 拆分、`BaseAgent.run()` 的 ReAct 循环 + `AgentState` 状态机。
- **为什么放中段**：它同时依赖「ChatClient（第 2 站）+ 工具（第 4 站）」两个前置概念，所以不能更早。它是面试最容易被深挖、也最容易讲错的点 —— **自动工具调用 vs 手动驱动的区别**，吃透这站，Agent 部分就通了。

### 第 6 站 · `rag/PgVectorVectorStoreConfig` + `doChatWithRag` —— 检索增强
- **学什么**：`EmbeddingModel + VectorStore` 三件套、`similaritySearch(topK)`、手动拼上下文 vs `QuestionAnswerAdvisor`、`@ConditionalOnBean` 优雅降级。
- **为什么放这**：RAG 是独立支线，不依赖 Agent。放在 Agent 之后是因为难度梯度平滑，且此时你已熟悉 Advisor，能秒懂「框架版 RAG 就是个 Advisor」。

### 第 7 站 · `doChatByStream` —— 流式输出
- **学什么**：`.stream().content()` 返回 `Flux<String>`、`doOnNext/doOnComplete/doOnError` 副作用钩子做埋点。
- **为什么放这**：流式是「调用形态」的变体，概念上不难，但需要一点 Reactor 基础。放在业务逻辑都懂了之后，单独攻流式细节最省力。

### 第 8 站 · `doChatWithMcp` + `config/McpCapabilityServerConfig` —— 跨进程工具
- **学什么**：MCP Client 把远程工具当本地工具、MCP Server 反向暴露能力、`@Resource(name="toolCallbacks")` 解决 Bean 歧义。
- **为什么放最后**：MCP 是工具能力的「联网升级版」，必须先有第 4/5 站的工具基础才有意义。它也是本项目对接 Dify 的关键，属于「锦上添花、面试加分」的进阶项。

### 学习建议
- **第 1–5 站是主线必修**，建议逐行读 + 自己画一遍调用时序图；
- **第 6–8 站是支线**，可按兴趣 / 面试侧重挑选；
- 每站读完后，回头看 `AssistantApp` 里对应的方法，验证「主干 ↔ 分支」是否对得上，形成闭环。

---

## 附：一句话速记

> 用 `ChatClient` 发起对话；用 `Advisor` 做横切（日志 / 记忆 / RAG）；用 `@Tool` + `ToolCallback` 给模型加手脚；
> 普通场景让框架**自动**调工具，做 Agent 就 `withProxyToolCalls(true)` + `ToolCallingManager` **手动**驱动 think/act 循环；
> RAG = `VectorStore.similaritySearch` 召回后拼进 prompt；MCP = 把工具跨进程联网。
> 全程只依赖接口，底层模型 / 存储 / 工具来源都可插拔替换。
