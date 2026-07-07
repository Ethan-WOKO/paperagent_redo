package com.yanban.api.agent;

import com.yanban.core.model.ChatMessage;
import java.util.List;

public record AgentRuntimeResult(
        boolean success,
        String assistantContent,
        List<ChatMessage> messages,
        int steps,
        String errorMessage,
        List<String> toolTrace,
        List<String> fallbacks,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
    public AgentRuntimeResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
        toolTrace = toolTrace == null ? List.of() : List.copyOf(toolTrace);
        fallbacks = fallbacks == null ? List.of() : List.copyOf(fallbacks);
    }
}
