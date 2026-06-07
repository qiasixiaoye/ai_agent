package com.vs.vsaiagent.eval.runner;

import com.vs.vsaiagent.app.AssistantApp;
import com.vs.vsaiagent.eval.EvalRunner;
import org.springframework.stereotype.Component;

/**
 * 把 case 输入打到 AssistantApp.doChat 上，使用独立 chatId 避免上下文串扰。
 */
@Component
public class AssistantAppRunner implements EvalRunner {

    public static final String NAME = "assistant_app";

    private final AssistantApp assistantApp;

    public AssistantAppRunner(AssistantApp assistantApp) {
        this.assistantApp = assistantApp;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public String run(String input, String chatId) {
        return assistantApp.doChat(input, chatId);
    }
}
