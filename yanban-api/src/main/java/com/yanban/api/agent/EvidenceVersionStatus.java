package com.yanban.api.agent;

/** Version governance state; legacy evidence is never presented as verified. */
public enum EvidenceVersionStatus {
    VERIFIED,
    STALE,
    LEGACY_UNVERSIONED
}
