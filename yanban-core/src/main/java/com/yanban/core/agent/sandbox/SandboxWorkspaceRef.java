package com.yanban.core.agent.sandbox;

import com.yanban.core.research.ProjectVersionRef;

/** Display-safe identity of an immutable Project snapshot; it carries no runtime authority. */
public record SandboxWorkspaceRef(long projectId, ProjectVersionRef projectVersion)
        implements RejectsUnknownFields {
    public SandboxWorkspaceRef {
        if (projectId < 1 || projectVersion == null) {
            throw new IllegalArgumentException("sandbox workspace requires a positive project id and immutable version");
        }
    }
}
