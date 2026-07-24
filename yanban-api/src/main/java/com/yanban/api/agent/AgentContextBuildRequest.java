package com.yanban.api.agent;

import java.util.List;

public record AgentContextBuildRequest(
        Long sessionId,
        Long userId,
        String providerKey,
        String modelName,
        String sessionSummary,
        AgentLongTermMemoryContext longTermMemoryContext,
        String ragContext,
        String toolTraceContext,
        Integer maxRecentMessages,
        Integer maxContextCharacters,
        AgentContextRetention retention,
        List<AgentContextEvidence> evidence,
        String currentUserMessage,
        AgentContextProjectState projectState
) {
    public AgentContextBuildRequest(Long sessionId,
                                    Long userId,
                                    String providerKey,
                                    String modelName,
                                    String sessionSummary,
                                    AgentLongTermMemoryContext longTermMemoryContext,
                                    String ragContext,
                                    String toolTraceContext,
                                    Integer maxRecentMessages,
                                    Integer maxContextCharacters,
                                    AgentContextRetention retention,
                                    List<AgentContextEvidence> evidence) {
        this(sessionId, userId, providerKey, modelName, sessionSummary, longTermMemoryContext, ragContext,
                toolTraceContext, maxRecentMessages, maxContextCharacters, retention, evidence, null, null);
    }

    public AgentContextBuildRequest(Long sessionId,
                                    Long userId,
                                    String providerKey,
                                    String modelName,
                                    String sessionSummary,
                                    Integer maxRecentMessages,
                                    Integer maxContextCharacters) {
        this(sessionId, userId, providerKey, modelName, sessionSummary, null, null, null, maxRecentMessages, maxContextCharacters, null, List.of(), null, null);
    }

    public AgentContextBuildRequest(Long sessionId,
                                    Long userId,
                                    String providerKey,
                                    String modelName,
                                    String sessionSummary,
                                    String ragContext,
                                    String toolTraceContext,
                                    Integer maxRecentMessages,
                                    Integer maxContextCharacters) {
        this(sessionId, userId, providerKey, modelName, sessionSummary, null, ragContext, toolTraceContext, maxRecentMessages, maxContextCharacters, null, List.of(), null, null);
    }

    public AgentContextBuildRequest(Long sessionId,
                                    Long userId,
                                    String providerKey,
                                    String modelName,
                                    String sessionSummary,
                                    AgentLongTermMemoryContext longTermMemoryContext,
                                    String ragContext,
                                    String toolTraceContext,
                                    Integer maxRecentMessages,
                                    Integer maxContextCharacters) {
        this(sessionId, userId, providerKey, modelName, sessionSummary, longTermMemoryContext, ragContext, toolTraceContext,
                maxRecentMessages, maxContextCharacters, null, List.of(), null, null);
    }

    public AgentContextBuildRequest {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
