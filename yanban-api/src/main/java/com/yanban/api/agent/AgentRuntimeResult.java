package com.yanban.api.agent;

import com.yanban.core.harness.HarnessResult;
import com.yanban.core.model.ChatMessage;
import java.util.List;

public record AgentRuntimeResult(
        boolean success,
        String assistantContent,
        List<ChatMessage> messages,
        int steps,
        String errorMessage
) {
    public AgentRuntimeResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static AgentRuntimeResult fromHarnessResult(HarnessResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        return new AgentRuntimeResult(
                result.success(),
                result.assistantContent(),
                result.messages(),
                result.steps(),
                result.errorMessage()
        );
    }
}
