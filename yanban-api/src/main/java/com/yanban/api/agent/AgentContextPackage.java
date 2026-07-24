package com.yanban.api.agent;

import com.yanban.core.model.ChatMessage;
import java.util.List;

public record AgentContextPackage(
        List<ChatMessage> messages,
        List<AgentContextSection> sections,
        List<AgentContextDroppedItem> droppedItems,
        int rawMessageCount,
        int normalizedMessageCount,
        int estimatedCharacters,
        EvidenceLedger evidenceLedger,
        ChatMessage currentUserMessage,
        AgentContextDebugView debugView
) {
    public AgentContextPackage(List<ChatMessage> messages,
                               List<AgentContextSection> sections,
                               List<AgentContextDroppedItem> droppedItems,
                               int rawMessageCount,
                               int normalizedMessageCount,
                               int estimatedCharacters) {
        this(messages, sections, droppedItems, rawMessageCount, normalizedMessageCount, estimatedCharacters,
                EvidenceLedger.empty(), null, null);
    }

    public AgentContextPackage(List<ChatMessage> messages,
                               List<AgentContextSection> sections,
                               List<AgentContextDroppedItem> droppedItems,
                               int rawMessageCount,
                               int normalizedMessageCount,
                               int estimatedCharacters,
                               EvidenceLedger evidenceLedger) {
        this(messages, sections, droppedItems, rawMessageCount, normalizedMessageCount, estimatedCharacters,
                evidenceLedger, null, null);
    }

    public AgentContextPackage(List<ChatMessage> messages,
                               List<AgentContextSection> sections,
                               List<AgentContextDroppedItem> droppedItems,
                               int rawMessageCount,
                               int normalizedMessageCount,
                               int estimatedCharacters,
                               EvidenceLedger evidenceLedger,
                               AgentContextDebugView debugView) {
        this(messages, sections, droppedItems, rawMessageCount, normalizedMessageCount, estimatedCharacters,
                evidenceLedger, null, debugView);
    }

    public AgentContextPackage {
        messages = messages == null ? List.of() : List.copyOf(messages);
        sections = sections == null ? List.of() : List.copyOf(sections);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
        evidenceLedger = evidenceLedger == null ? EvidenceLedger.empty() : evidenceLedger;
        if (currentUserMessage != null && !"user".equals(currentUserMessage.role())) {
            throw new IllegalArgumentException("currentUserMessage must use the user role");
        }
    }
}
