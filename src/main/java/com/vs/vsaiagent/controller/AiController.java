package com.vs.vsaiagent.controller;

import com.vs.vsaiagent.agent.VsManus;
import com.vs.vsaiagent.agent.ManusConversationMemory;
import com.vs.vsaiagent.agent.model.AgentState;
import com.vs.vsaiagent.app.AssistantApp;
import com.vs.vsaiagent.orchestration.OrchestrationRequest;
import com.vs.vsaiagent.orchestration.RequestOrchestrator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AssistantApp assistantApp;

    @Resource
    private ToolCallback[] allTools;

    @Autowired
    private ChatModel chatModel;

    @Resource
    private RequestOrchestrator requestOrchestrator;

    @Resource
    private ManusConversationMemory manusConversationMemory;

    @Autowired
    @Qualifier("manusAgentExecutor")
    private Executor manusAgentExecutor;

    @PostMapping(value = "/orchestrate/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<com.vs.vsaiagent.orchestration.OrchestrationEvent>> orchestrate(
            @RequestBody OrchestrationRequest request) {
        return requestOrchestrator.stream(request);
    }

    @GetMapping(value = "/orchestrate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<com.vs.vsaiagent.orchestration.OrchestrationEvent>> orchestrateGet(
            String message, String conversationId, String requestId, String traceId, String confirmationToken) {
        return requestOrchestrator.stream(new OrchestrationRequest(
                conversationId, message, confirmationToken, requestId, traceId));
    }

    @GetMapping("/assistant_app/chat/sync")
    public String doChatWithAppSync(String message, String chatId) {
        return assistantApp.doChat(message, chatId);
    }

    /**
     * Skill 对话（同步）：让 LLM 在对话中自主调用已注册 Skill（含会内部再编排工具的结构化 Skill）。
     * 例：message=「帮我做北京 2026-06-25 的银河拍摄计划，纬度39.9 经度116.4」→ LLM 选 astro-shoot-plan。
     */
    @GetMapping("/assistant_app/chat_skills/sync")
    public String doChatWithSkillsSync(String message, String chatId) {
        return assistantApp.doChatWithSkills(message, chatId);
    }

    @GetMapping(value = "/assistant_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithAppSse(String message, String chatId) {
        return assistantApp.doChatByStream(message, chatId);
    }

    @GetMapping(value = "/assistant_app/chat_rag/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithRagSse(String message, String chatId) {
        return assistantApp.doChatWithRagSse(message, chatId);
    }

    @GetMapping(value = "/assistant_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithAppServerSentEvent(String message, String chatId) {
        return assistantApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    @GetMapping(value = "/assistant_app/chat/sse_emitter")
    public SseEmitter doChatWithAppSseEmitter(String message, String chatId) {
        SseEmitter sseEmitter = new SseEmitter(180000L);
        assistantApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        return sseEmitter;
    }

    @GetMapping(value = "/manus/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter doChatWithManus(String message, String sessionId) {
        VsManus vsManus = new VsManus(allTools, chatModel, manusAgentExecutor);
        List<org.springframework.ai.chat.messages.Message> history = manusConversationMemory.restore(sessionId);
        vsManus.setMessageList(history);
        SseEmitter emitter = vsManus.runStream(message);
        emitter.onCompletion(() -> {
            if (vsManus.getState() == AgentState.FINISHED) {
                manusConversationMemory.persistNewMessages(sessionId, vsManus.getMessageList(), history.size());
            }
        });
        return emitter;
    }
}
