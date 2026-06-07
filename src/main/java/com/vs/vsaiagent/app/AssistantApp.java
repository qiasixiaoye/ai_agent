package com.vs.vsaiagent.app;


import com.vs.vsaiagent.advisor.MyLoggerAdvisor;
import com.vs.vsaiagent.chatmemory.FileBasedChatMemory;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import com.vs.vsaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
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
                        @Value("${spring.ai.openai.chat.options.model:unknown}") String modelName) {
        this.executionLogService = executionLogService;
        this.modelName = modelName;
        // 基于文件
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor()
                )
                .build();
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
                .system(SYSTEM_PROMPT + "本次对话结束时输出一份报告，title=对当前话题的简短描述，suggestions=可执行建议列表")
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
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .user(message)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new MyLoggerAdvisor())
                    .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
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
                    .system(SYSTEM_PROMPT + "如果检索到了文档，请在最后列出检索到的文档内容；如果没有检索到文档，请基于自身知识尽量准确地回答。")
                    .user(message)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new MyLoggerAdvisor())
                    .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
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

    // AI 调用 MCP 服务
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * MCP 调用对话（同步）。
     */
    public String doChatWithMcp(String message, String chatId) {
        long start = System.currentTimeMillis();
        String requestId = executionLogService.startRequest("chat_mcp_sync", chatId, message, modelName);
        try {
            long modelStart = System.currentTimeMillis();
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .user(message)
                    .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                    .advisors(new MyLoggerAdvisor())
                    .tools(toolCallbackProvider)
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
