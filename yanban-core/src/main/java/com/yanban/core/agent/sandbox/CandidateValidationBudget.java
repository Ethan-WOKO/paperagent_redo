package com.yanban.core.agent.sandbox;

/** Deterministic upper bounds for contract validation work. */
public record CandidateValidationBudget(int maxChanges, int maxEvidenceRefs, long maxCandidateUtf8Bytes)
        implements RejectsUnknownFields {
    public CandidateValidationBudget {
        if (maxChanges < 1 || maxEvidenceRefs < 1 || maxCandidateUtf8Bytes < 0) {
            throw new IllegalArgumentException("candidate validation budget limits must be positive");
        }
    }
}
