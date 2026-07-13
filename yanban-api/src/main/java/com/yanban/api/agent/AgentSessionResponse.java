package com.yanban.api.agent;

import com.yanban.core.agent.AgentSession;
import com.yanban.core.agent.AgentSessionScope;
import java.time.Instant;

public record AgentSessionResponse(
        Long id,
        Long userId,
        AgentSessionScope scope,
        Long projectId,
        String title,
        String modelProvider,
        String model,
        Integer maxSteps,
        Boolean ragDisabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static AgentSessionResponse from(AgentSession session) {
        return new AgentSessionResponse(
                session.getId(),
                session.getUserId(),
                session.getScope(),
                session.getProjectId(),
                session.getTitle(),
                session.getModelProviderSnapshot(),
                session.getModelSnapshot(),
                session.getMaxSteps(),
                session.getRagDisabled(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
