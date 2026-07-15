package com.yanban.core.agent;

/** Stable identity of one user-request run; source/sourceId adapt existing Turn, Plan, Paper and Literature rows. */
public record AgentRunIdentity(String source, String sourceId, Long userId, Long sessionId, Long projectId) {
    public AgentRunIdentity {
        if (source == null || source.isBlank() || sourceId == null || sourceId.isBlank() || userId == null) {
            throw new IllegalArgumentException("run source, sourceId and userId are required");
        }
        source = source.trim();
        sourceId = sourceId.trim();
    }

    public String runId() {
        return source + ":" + sourceId;
    }
}
