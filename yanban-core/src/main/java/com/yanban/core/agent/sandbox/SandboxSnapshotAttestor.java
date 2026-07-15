package com.yanban.core.agent.sandbox;

import com.yanban.core.research.ResearchRuntimeScope;

/** Boundary for a future server adapter after it resolves the current Project manifest. */
public final class SandboxSnapshotAttestor {
    public static final String REQUIRED_READ_CAPABILITY = "research:project-read";

    private SandboxSnapshotAttestor() { }

    public static SandboxSnapshotAttestation attestServerResolved(ResearchRuntimeScope runtimeAuthority,
                                                                  SandboxWorkspaceSnapshot snapshot) {
        if (runtimeAuthority == null || snapshot == null) {
            throw new IllegalArgumentException("runtime authority and server-resolved snapshot must not be null");
        }
        runtimeAuthority.requireCapability(REQUIRED_READ_CAPABILITY);
        if (runtimeAuthority.trustedProjectId() != snapshot.workspace().projectId()) {
            throw new IllegalArgumentException("runtime authority does not own the sandbox Project");
        }
        if (!runtimeAuthority.projectVersion().equals(snapshot.workspace().projectVersion())) {
            throw new IllegalArgumentException("runtime authority does not attest the sandbox Project version");
        }
        return new SandboxSnapshotAttestation(snapshot);
    }
}
