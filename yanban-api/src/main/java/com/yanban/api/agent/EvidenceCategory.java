package com.yanban.api.agent;

/** Small, stable evidence taxonomy consumed by final-answer synthesis. */
public enum EvidenceCategory {
    EXECUTION_FACT,
    VERIFIED_PROJECT_EVIDENCE,
    EXTERNAL_SOURCE,
    INFERENCE,
    UNVERIFIED_INPUT
}
