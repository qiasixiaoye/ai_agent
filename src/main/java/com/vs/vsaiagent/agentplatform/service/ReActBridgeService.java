package com.vs.vsaiagent.agentplatform.service;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReActBridgeService {

    public List<Message> backfillToolResult(List<Message> history, ToolExecuteResult result) {
        List<Message> merged = history == null ? new ArrayList<>() : new ArrayList<>(history);
        String payload = """
                tool=%s
                success=%s
                output=%s
                error=%s
                """.formatted(result.getToolName(), result.isSuccess(), safe(result.getOutput()), safe(result.getErrorMessage()));
        merged.add(new UserMessage("工具执行观察结果:\n" + payload));
        return merged;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
