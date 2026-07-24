package com.yanban.api.agent;

import org.springframework.util.StringUtils;

/** Authenticated Project identity made available to the context builder. */
public record AgentContextProjectState(Long projectId, String projectVersion) {
    public AgentContextProjectState {
        if (projectId == null || projectId < 1) {
            throw new IllegalArgumentException("projectId must be positive");
        }
        if (!StringUtils.hasText(projectVersion)) {
            throw new IllegalArgumentException("projectVersion must not be blank");
        }
    }
}
