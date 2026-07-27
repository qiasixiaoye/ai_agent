package com.vs.vsaiagent.agent;

import com.vs.vsaiagent.memory.HierarchicalChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ManusConversationMemory {

    private static final int HISTORY_MESSAGE_LIMIT = 20;

    private final HierarchicalChatMemory memory;

    public ManusConversationMemory(HierarchicalChatMemory memory) {
        this.memory = memory;
    }

    public List<Message> restore(String sessionId) {
        return new ArrayList<>(memory.get(sessionId, HISTORY_MESSAGE_LIMIT));
    }

    public void persistNewMessages(String sessionId, List<Message> allMessages, int existingMessageCount) {
        if (allMessages == null || existingMessageCount >= allMessages.size()) {
            return;
        }
        List<Message> conversationMessages = allMessages.subList(Math.max(0, existingMessageCount), allMessages.size())
                .stream()
                .filter(message -> message.getMessageType() == MessageType.USER
                        || message.getMessageType() == MessageType.ASSISTANT)
                .toList();
        memory.add(sessionId, conversationMessages);
    }
}
