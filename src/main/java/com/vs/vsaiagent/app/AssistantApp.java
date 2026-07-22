package com.vs.vsaiagent.app;


import com.vs.vsaiagent.advisor.MyLoggerAdvisor;
import com.vs.vsaiagent.context.BudgetedToolCallback;
import com.vs.vsaiagent.context.ContextBudgetManager;
import com.vs.vsaiagent.context.ContextBudgetPlan;
import com.vs.vsaiagent.context.ToolResultCompressor;
import com.vs.vsaiagent.memory.ContextAssembly;
import com.vs.vsaiagent.memory.ContextWindowManager;
import com.vs.vsaiagent.memory.HierarchicalChatMemory;
import com.vs.vsaiagent.mcp.management.ManagedMcpToolService;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import com.vs.vsaiagent.observability.tool.LoggingToolCallback;
import com.vs.vsaiagent.rag.QueryRewriter;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.adapter.SkillCallbackAdapter;
import com.vs.vsaiagent.skill.context.SkillContextAssembly;
import com.vs.vsaiagent.skill.context.SkillContextManager;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import com.vs.vsaiagent.skill.routing.SkillRouteDecision;
import com.vs.vsaiagent.skill.routing.SkillRouter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

/**
 * 业务壳层：通用 AI 助手。
 *
 * 装配 ChatClient + ChatMemory + Advisor + Tools + MCP，对外暴露
 * 同步对话、流式对话、RAG 增强、工具调用、MCP 调用 五种调用入口。
 *
 * 这一层负责「业务话术 + 调用入口」，具体能力（工具 / RAG / 编排）下沉到
 * tools / rag / agentplatform 模块。后续可以基于同一套底座衍生多个领域助手。
 */
@Component
@Slf4j
public class AssistantApp {

    private final ChatClient chatClient;
    private final ExecutionLogService executionLogService;
    private final String modelName;
    private final ContextWindowManager contextWindowManager;
    private final HierarchicalChatMemory hierarchicalChatMemory;
    private final ContextBudgetManager contextBudgetManager;
    private final ToolResultCompressor toolResultCompressor;
    private final SkillContextManager skillContextManager;

    private static final String SYSTEM_PROMPT = """
            你是一个通用 AI 助手，承担两类任务：
            1. 围绕用户提出的问题进行准确、详尽、可执行的解答；
            2. 在用户需要时主动调用平台提供的工具或知识库，把外部能力整合进答案。

            原则：
            - 第一次对话时简要自我介绍，并请用户说明使用场景或目标领域；
            - 涉及具体事实、数据、最新进展时优先调用工具或 RAG 检索；
            - 回答务必结构清晰，关键结论先行，必要时给出步骤或示例；
            - 不确定的内容明确说明不确定，不要编造。
            """;

    public AssistantApp(ChatModel dashscopeChatModel,
                        ExecutionLogService executionLogService,
                        HierarchicalChatMemory hierarchicalChatMemory,
                        ContextWindowManager contextWindowManager,
                        ContextBudgetManager contextBudgetManager,
                        ToolResultCompressor toolResultCompressor,
                        SkillContextManager skillContextManager,
                        @Value("${spring.ai.dashscope.chat.options.model:dashscope}") String modelName) {
        this.executionLogService = executionLogService;
        this.modelName = modelName;
        this.hierarchicalChatMemory = hierarchicalChatMemory;
        this.contextWindowManager = contextWindowManager;
        this.contextBudgetManager = contextBudgetManager;
        this.toolResultCompressor = toolResultCompressor;
        this.skillContextManager = skillContextManager;
        ChatMemory chatMemory = hierarchicalChatMemory;

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    private String systemPromptWithMemory(String message, String chatId, String requestId) {
        return systemPromptWithMemory(message, chatId, requestId, "", null);
    }

    private String systemPromptWithMemory(String message, String chatId, String requestId,
                                          String extraContext, ToolCallback[] callbacks) {
        long start = System.currentTimeMillis();
        ContextBudgetPlan plan = contextBudgetManager.plan(SYSTEM_PROMPT, message, extraContext, callbacks);
        ContextAssembly assembly = contextWindowManager.assemble(chatId, message, plan);
        executionLogService.logStage(requestId, ExecutionStageType.RETRIEVAL, "memory_context_assemble", null,
                message, java.util.Map.of("budget", plan, "memory", assembly).toString(),
                System.currentTimeMillis() - start, true, null);
        return SYSTEM_PROMPT + (extraContext == null ? "" : extraContext) + assembly.contextText();
    }

    /**
     * 基础对话（同步）。
     */
    public String doChat(String message, String chatId) {
        long start = System.currentTimeMillis();
        String requestId = executionLogService.startRequest("chat_sync", chatId, message, modelName);
        try {
            long modelStart = System.currentTimeMillis();
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .system(systemPromptWithMemory(message, chatId, requestId))
                    .user(message)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .call()
                    .chatResponse();
            String content = chatResponse.getResult().getOutput().getText();
            executionLogService.logStage(requestId, ExecutionStageType.MODEL, "model_generate", null,
                    message, content, System.currentTimeMillis() - modelStart, true, null);
            executionLogService.finishSuccess(requestId, content, System.currentTimeMillis() - start);
            log.info("content: {}", content);
            return content;
        } catch (Exception e) {
            executionLogService.finishFail(requestId, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    /** 简单结构化报告：标题 + 建议列表。供 doChatWithReport 序列化使用。 */
    record ConversationReport(String title, List<String> suggestions) {
    }

    /**
     * 基础对话 SSE。
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        long start = System.currentTimeMillis();
        String requestId = executionLogService.startRequest("chat_stream", chatId, message, modelName);
        List<String> chunks = new ArrayList<>();
        Flux<String> content = chatClient
                .prompt()
                .system(systemPromptWithMemory(message, chatId, requestId))
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content()
                .doOnNext(chunks::add)
                .doOnComplete(() -> {
                    String finalOutput = String.join("", chunks);
                    executionLogService.logStage(requestId, ExecutionStageType.MODEL, "model_stream_generate", null,
                            message, finalOutput, System.currentTimeMillis() - start, true, null);
                    executionLogService.finishSuccess(requestId, finalOutput, System.currentTimeMillis() - start);
                })
                .doOnError(e -> executionLogService.finishFail(requestId, e.getMessage(), System.currentTimeMillis() - start));
        log.info("content: {}", content);
        return content;
    }

    /**
     * 生成结构化报告。
     */
    public ConversationReport doChatWithReport(String message, String chatId) {
        ConversationReport report = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + contextWindowManager.assemble(chatId, message).contextText()
                        + "本次对话结束时输出一份报告，title=对当前话题的简短描述，suggestions=可执行建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(ConversationReport.class);
        log.info("ConversationReport: {}", report);
        return report;
    }

    // 基于内存的 rag 检索
    @Autowired(required = false)
    private VectorStore assistantAppVectorStore;

    // 云rag
    @Autowired(required = false)
    private Advisor assistantAppRagCloudAdvisor;
    // 基于 服务器 pg 向量检索
    @Autowired(required = false)
    private VectorStore pgVectorVectorStore;

    // 查询重写
    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 把一次检索得到的文档拼成上下文，追加到 system 提示中。
     * 没有检索结果时返回空串，让模型回退到自身知识。
     */
    private String buildRagContext(List<Document> recalls) {
        if (recalls == null || recalls.isEmpty()) {
            return "\n\n（本次未检索到相关资料，请基于自身知识尽量准确回答，并说明信息可能不完整。）";
        }
        String context = recalls.stream()
                .map(document -> document.getText() == null ? "" : document.getText())
                .collect(Collectors.joining("\n---\n"));
        context = contextBudgetManager.fit(ContextBudgetManager.RAG, context);
        return "\n\n以下是检索到的参考资料，请优先依据其回答；若资料不足，再结合自身知识并明确说明：\n" + context;
    }

    /**
     * RAG 增强对话（同步）。
     */
    public String doChatWithRag(String message, String chatId) {
        if (pgVectorVectorStore == null) {
            return doChat(message, chatId);
        }
        long start = System.currentTimeMillis();
        String requestId = executionLogService.startRequest("chat_rag_sync", chatId, message, modelName);
        try {
            long retrieveStart = System.currentTimeMillis();
            List<Document> recalls = pgVectorVectorStore.similaritySearch(
                    SearchRequest.builder().query(message).topK(4).build());
            String summary = recalls.stream()
                    .map(document -> document.getText() == null ? "" : document.getText())
                    .map(text -> text.length() > 120 ? text.substring(0, 120) : text)
                    .collect(Collectors.joining("\n---\n"));
            executionLogService.logStage(requestId, ExecutionStageType.RETRIEVAL, "rag_recall", null,
                    message, "count=" + recalls.size() + "\n" + summary, System.currentTimeMillis() - retrieveStart, true, null);

            long modelStart = System.currentTimeMillis();
            // 复用上面的一次检索结果作为上下文，避免 QuestionAnswerAdvisor 再查一遍向量库
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .system(systemPromptWithMemory(message, chatId, requestId,
                            buildRagContext(recalls), null))
                    .user(message)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .call()
                    .chatResponse();
            String content = chatResponse.getResult().getOutput().getText();
            executionLogService.logStage(requestId, ExecutionStageType.MODEL, "model_generate", null,
                    message, content, System.currentTimeMillis() - modelStart, true, null);
            executionLogService.finishSuccess(requestId, content, System.currentTimeMillis() - start);
            log.info("content: {}", content);
            return content;
        } catch (Exception e) {
            executionLogService.finishFail(requestId, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    /**
     * RAG 增强对话（流式）。
     */
    public Flux<String> doChatWithRagSse(String message, String chatId) {
        if (pgVectorVectorStore == null) {
            return doChatByStream(message, chatId);
        }
        long start = System.currentTimeMillis();
        String requestId = executionLogService.startRequest("chat_rag_stream", chatId, message, modelName);
        try {
            long retrieveStart = System.currentTimeMillis();
            List<Document> recalls = pgVectorVectorStore.similaritySearch(
                    SearchRequest.builder().query(message).topK(4).build());
            String summary = recalls.stream()
                    .map(document -> document.getText() == null ? "" : document.getText())
                    .map(text -> text.length() > 120 ? text.substring(0, 120) : text)
                    .collect(Collectors.joining("\n---\n"));
            executionLogService.logStage(requestId, ExecutionStageType.RETRIEVAL, "rag_recall", null,
                    message, "count=" + recalls.size() + "\n" + summary, System.currentTimeMillis() - retrieveStart, true, null);

            List<String> chunks = new ArrayList<>();
            Flux<String> content = chatClient
                    .prompt()
                    .system(systemPromptWithMemory(message, chatId, requestId,
                            buildRagContext(recalls), null))
                    .user(message)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .stream()
                    .content()
                    .doOnNext(chunks::add)
                    .doOnComplete(() -> {
                        String finalOutput = String.join("", chunks);
                        executionLogService.logStage(requestId, ExecutionStageType.MODEL, "model_stream_generate", null,
                                message, finalOutput, System.currentTimeMillis() - start, true, null);
                        executionLogService.finishSuccess(requestId, finalOutput, System.currentTimeMillis() - start);
                    })
                    .doOnError(e -> executionLogService.finishFail(requestId, e.getMessage(), System.currentTimeMillis() - start));
            log.info("content: {}", content);
            return content;
        } catch (Exception e) {
            executionLogService.finishFail(requestId, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * 工具调用对话（同步）。
     */
    public String doChatWithTools(String message, String chatId) {
        long start = System.currentTimeMillis();
        String requestId = executionLogService.startRequest("chat_tools_sync", chatId, message, modelName);
        try {
            long modelStart = System.currentTimeMillis();
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .system(systemPromptWithMemory(message, chatId, requestId, "", allTools))
                    .user(message)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new MyLoggerAdvisor())
                    .tools(allTools)
                    .call()
                    .chatResponse();
            String content = chatResponse.getResult().getOutput().getText();
            executionLogService.logStage(requestId, ExecutionStageType.MODEL, "model_generate", null,
                    message, content, System.currentTimeMillis() - modelStart, true, null);
            executionLogService.finishSuccess(requestId, content, System.currentTimeMillis() - start);
            log.info("content: {}", content);
            return content;
        } catch (Exception e) {
            executionLogService.finishFail(requestId, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    // Skill 注册表：把已注册 Skill 适配成工具喂给 LLM，让 agent 能自主决定调用某个 Skill。
    @Autowired(required = false)
    private SkillRegistry skillRegistry;

    @Autowired(required = false)
    private SkillRouter skillRouter;

    /**
     * Skill 对话（同步）：把所有已注册 Skill 适配成 ToolCallback 注入 ChatClient，
     * 由 LLM 在对话中自主决定调用哪个 Skill。区别于 {@link #doChatWithTools}（原子工具），
     * 这里 LLM 能调用到「结构化 Skill」（如 astro-shoot-plan，内部还会再编排多个工具）。
     */
    public String doChatWithSkills(String message, String chatId) {
        long start = System.currentTimeMillis();
        String requestId = executionLogService.startRequest("chat_skills_sync", chatId, message, modelName);
        try {
            long routeStart = System.currentTimeMillis();
            SkillRouteDecision decision = skillRouter == null
                    ? new SkillRouteDecision(message, 0, 10, true,
                        (skillRegistry == null ? List.<Skill>of() : skillRegistry.listAll()).stream().map(Skill::name).toList(),
                        List.of())
                    : skillRouter.route(message);
            executionLogService.logStage(requestId, ExecutionStageType.RETRIEVAL, "skill_route", null,
                    message, decision.toString(), System.currentTimeMillis() - routeStart, true, null);

            ToolCallback[] skillTools = decision.selectedSkillNames().stream()
                    .map(name -> skillRegistry == null ? null : skillRegistry.find(name).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .map(s -> {
                        ToolCallback logged = new LoggingToolCallback(
                                new SkillCallbackAdapter(s), executionLogService);
                        return (ToolCallback) new BudgetedToolCallback(
                                logged, toolResultCompressor, executionLogService);
                    })
                    .toArray(ToolCallback[]::new);
            SkillContextAssembly skillContext = skillContextManager.assemble(decision);
            executionLogService.logStage(requestId, ExecutionStageType.RETRIEVAL,
                    "skill_context_load", null, decision.toString(), skillContext.toString(),
                    0L, true, null);

            long modelStart = System.currentTimeMillis();
            ChatResponse chatResponse;
            if (skillTools.length == 0) {
                chatResponse = chatClient.prompt().user(message)
                        .system(systemPromptWithMemory(message, chatId, requestId,
                                skillContext.contextText(), skillTools))
                        .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                        .call().chatResponse();
            } else {
                chatResponse = chatClient.prompt().user(message)
                        .system(systemPromptWithMemory(message, chatId, requestId,
                                skillContext.contextText(), skillTools))
                        .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                        .tools(skillTools)
                        .call().chatResponse();
            }
            String content = chatResponse.getResult().getOutput().getText();
            executionLogService.logStage(requestId, ExecutionStageType.MODEL, "model_generate", null,
                    message, content, System.currentTimeMillis() - modelStart, true, null);
            executionLogService.finishSuccess(requestId, content, System.currentTimeMillis() - start);
            log.info("content: {}", content);
            return content;
        } catch (Exception e) {
            executionLogService.finishFail(requestId, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    @Autowired
    private ManagedMcpToolService managedMcpToolService;

    /**
     * MCP 调用对话（同步）。
     */
    public String doChatWithMcp(String message, String chatId) {
        long start = System.currentTimeMillis();
        String requestId = executionLogService.startRequest("chat_mcp_sync", chatId, message, modelName);
        try {
            long modelStart = System.currentTimeMillis();
            ToolCallback[] managedMcpTools = managedMcpToolService.allowedToolCallbacks(message);
            if (managedMcpTools.length == 0) {
                throw new IllegalStateException("没有通过治理策略且处于可用状态的 MCP 工具");
            }
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .system(systemPromptWithMemory(message, chatId, requestId, "", managedMcpTools))
                    .user(message)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new MyLoggerAdvisor())
                    .tools(managedMcpTools)
                    .call()
                    .chatResponse();
            String content = chatResponse.getResult().getOutput().getText();
            executionLogService.logStage(requestId, ExecutionStageType.MODEL, "model_generate", null,
                    message, content, System.currentTimeMillis() - modelStart, true, null);
            executionLogService.finishSuccess(requestId, content, System.currentTimeMillis() - start);
            log.info("content: {}", content);
            return content;
        } catch (Exception e) {
            executionLogService.finishFail(requestId, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }
}
