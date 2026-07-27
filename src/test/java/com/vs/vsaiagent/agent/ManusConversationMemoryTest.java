package com.vs.vsaiagent.agent;

import com.vs.vsaiagent.memory.HierarchicalChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManusConversationMemoryTest {

    @TempDir
    Path tempDir;

    @Test
    void restoresBoundedHistoryAndPersistsOnlyUserAndAssistantMessagesFromANewRun() {
        HierarchicalChatMemory memory = new HierarchicalChatMemory(tempDir.toString(), 4, 1000, 20);
        ManusConversationMemory manuscriptMemory = new ManusConversationMemory(memory);
        memory.add("manus-session", List.of(new UserMessage("earlier question"), new AssistantMessage("earlier answer")));

        List<Message> restored = manuscriptMemory.restore("manus-session");
        manuscriptMemory.persistNewMessages("manus-session", List.of(
                restored.getFirst(), restored.getLast(),
                new UserMessage("new question"),
                new ToolResponseMessage(List.of()),
                new AssistantMessage("new answer")), restored.size());

        assertEquals(List.of("earlier question", "earlier answer", "new question", "new answer"),
                memory.get("manus-session", 10).stream().map(Message::getText).toList());
    }
}
