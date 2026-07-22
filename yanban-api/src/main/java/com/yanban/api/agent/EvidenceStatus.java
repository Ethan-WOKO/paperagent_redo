package com.yanban.api.agent;

/** Qualitative evidence state. Deliberately not a numeric confidence score. */
public enum EvidenceStatus {
    VERIFIED,
    SUPPORTED,
    INFERRED,
    UNVERIFIED,
    CONFLICTING,
    STALE
}
