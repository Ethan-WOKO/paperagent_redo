package com.yanban.api.agent;

/**
 * Server-owned identity attached after authenticated Project authorization.  It is never
 * decoded from user text, RAG data, or model tool arguments.
 */
public record ProjectRuntimeContext(Long userId, Long projectId, String projectVersion) {
    public ProjectRuntimeContext(Long userId, Long projectId) {
        this(userId, projectId, null);
    }

    public ProjectRuntimeContext {
        if (userId == null || projectId == null) {
            throw new IllegalArgumentException("project runtime context requires userId and projectId");
        }
    }
}
