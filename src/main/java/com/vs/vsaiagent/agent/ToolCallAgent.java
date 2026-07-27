package com.vs.vsaiagent.agent;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.vs.vsaiagent.agent.model.AgentState;
import com.vs.vsaiagent.agent.model.ManusStreamEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具智能体
 */

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private static final int MAX_MODEL_ATTEMPTS = 3;
    private static final Duration INITIAL_RETRY_BACKOFF = Duration.ofMillis(200);

    // 可用工具
    private final ToolCallback[] availableTools;


    // 保存工具调用信息的响应结果
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;
    public ToolCallAgent(ToolCallback[] availableTools)  {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .withProxyToolCalls(true) // spring ai 不会自主调用工具
                .build();

    }

    /**
     * 推理 选择工具
     * @return
     */
    @Override
    public boolean think() {
        // 校验prompt
        if (StrUtil.isNotBlank(getNextStepPrompt())){
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }

        //调用llm，得到工具调用的结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList,this.chatOptions);
        ChatResponse chatResponse = callWithRetry(prompt);
            // 解析工具调用结果，获取要调用的工具
            this.toolCallChatResponse = chatResponse;
            // 得到llm 的回答
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // ai 选择的工具
            List<AssistantMessage.ToolCall> toolCallsList = assistantMessage.getToolCalls();
            String result = assistantMessage.getText();
            log.info(getName() + "思考：" + result);
            log.info(getName()  + "调用了" + toolCallsList.size() + "个工具");
            String toolCallInfo = toolCallsList.stream()
                    .map(toolcall -> String.format("工具名称%s,参数%s", toolcall.name(), toolcall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info("工具调用详情：\\n{}",toolCallInfo);
            if (toolCallsList.isEmpty()) {
                getMessageList().add(assistantMessage);
                return false;
            }
        return true;

    }

    protected ChatResponse callPrompt(Prompt prompt) {
        return getChatClient().prompt(prompt)
                .system(getSystemPrompt())
                .tools(this.availableTools)
                .call()
                .chatResponse();
    }

    private ChatResponse callWithRetry(Prompt prompt) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_MODEL_ATTEMPTS; attempt++) {
            try {
                return callPrompt(prompt);
            } catch (RuntimeException e) {
                lastFailure = e;
                if (!isRetryable(e) || attempt == MAX_MODEL_ATTEMPTS) {
                    throw e;
                }
                log.warn("{} model call failed transiently; retrying attempt {}/{}", getName(),
                        attempt + 1, MAX_MODEL_ATTEMPTS, e);
                sleepBeforeRetry(attempt);
            }
        }
        throw lastFailure;
    }

    protected void sleepBeforeRetry(int retryAttempt) {
        long delayMillis = INITIAL_RETRY_BACKOFF.toMillis() * (1L << (retryAttempt - 1));
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Model retry interrupted", e);
        }
    }

    private boolean isRetryable(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof IOException || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String signal = (current.getClass().getSimpleName() + " " + current.getMessage())
                    .toLowerCase(Locale.ROOT);
            if (signal.contains("429") || signal.contains("rate limit") || signal.contains("timeout")
                    || signal.contains("temporar") || signal.contains("503")
                    || signal.contains("service unavailable") || signal.contains("connection reset")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Streams model deltas to the caller while keeping a complete response for
     * the existing tool execution path.
     */
    public boolean thinkStream(int step, ManusStreamEventEmitter emitter) throws IOException {
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));
        }

        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        List<ChatResponse> chunks;
        try {
            chunks = streamPrompt(prompt)
                    .doOnNext(response -> emitThinkingDelta(step, emitter, response))
                    .collectList()
                    .block();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        if (CollUtil.isEmpty(chunks)) {
            throw new IllegalStateException("Model stream returned no response");
        }

        ChatResponse chatResponse = assembleResponse(chunks);
        this.toolCallChatResponse = chatResponse;
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        if (assistantMessage.getToolCalls().isEmpty()) {
            getMessageList().add(assistantMessage);
            return false;
        }
        return true;
    }

    protected Flux<ChatResponse> streamPrompt(Prompt prompt) {
        return getChatClient().prompt(prompt)
                .system(getSystemPrompt())
                .tools(this.availableTools)
                .stream()
                .chatResponse();
    }

    private void emitThinkingDelta(int step, ManusStreamEventEmitter emitter, ChatResponse response) {
        String text = response.getResult().getOutput().getText();
        if (StrUtil.isBlank(text)) {
            return;
        }
        try {
            emitter.emit(ManusStreamEvent.thinking(step, text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ChatResponse assembleResponse(List<ChatResponse> chunks) {
        StringBuilder text = new StringBuilder();
        Map<String, Object> metadata = new LinkedHashMap<>();
        List<AssistantMessage.ToolCall> toolCalls = List.of();
        ChatResponse last = chunks.get(chunks.size() - 1);

        for (ChatResponse chunk : chunks) {
            AssistantMessage output = chunk.getResult().getOutput();
            if (output.getText() != null) {
                text.append(output.getText());
            }
            if (output.getMetadata() != null) {
                metadata.putAll(output.getMetadata());
            }
            if (!output.getToolCalls().isEmpty()) {
                toolCalls = new ArrayList<>(output.getToolCalls());
            }
        }

        AssistantMessage assistantMessage = new AssistantMessage(text.toString(), metadata, toolCalls);
        return new ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(assistantMessage)), last.getMetadata());
    }

    @Override
    protected boolean streamStep(int step, ManusStreamEventEmitter emitter) throws IOException {
        if (!thinkStream(step, emitter)) {
            emitter.emit(ManusStreamEvent.answer(step, getToolCallChatResponse().getResult().getOutput().getText()));
            setState(AgentState.FINISHED);
            return true;
        }

        for (AssistantMessage.ToolCall toolCall : getToolCallChatResponse().getResult().getOutput().getToolCalls()) {
            emitter.emit(ManusStreamEvent.toolCall(step, toolCall.name(), toolCall.arguments()));
        }

        act();
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(getMessageList());
        for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
            emitter.emit(ManusStreamEvent.toolResult(step, response.name(), response.responseData()));
        }
        return getState() == AgentState.FINISHED;
    }

    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没用工具需要调用";
        }
        Prompt prompt = new Prompt(getMessageList(),this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, this.toolCallChatResponse);
        // 记录消息上下文,conversationHistory 包含了助手消息和工具返回结果，可以看源码
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        // 判断是否调用终止工具
        boolean doTerminate = toolResponseMessage.getResponses().stream()
                .anyMatch(toolResponse -> toolResponse.name().equals("doTerminate"));
        if (doTerminate) {
            // 任务结束
            setState(AgentState.FINISHED);
        }
        String result = toolResponseMessage.getResponses().stream()
                .map(toolResponse -> "工具:" + toolResponse.name() + "返回结果:" + toolResponse.responseData())
                .collect(Collectors.joining("\n"));
        log.info("工具调用的结果：\\n{}",result);
        return result;
    }
}
