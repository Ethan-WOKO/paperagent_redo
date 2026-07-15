package com.yanban.core.agent.sandbox;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/** Non-serializable marker that a server resolved and manifest-verified this current snapshot. */
@JsonSerialize(using = SandboxSnapshotAttestationSerializer.class)
public final class SandboxSnapshotAttestation {
    private final SandboxWorkspaceSnapshot snapshot;

    SandboxSnapshotAttestation(SandboxWorkspaceSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    SandboxWorkspaceSnapshot snapshot() { return snapshot; }
}
