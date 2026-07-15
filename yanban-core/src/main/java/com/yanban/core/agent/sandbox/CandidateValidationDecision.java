package com.yanban.core.agent.sandbox;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/** Server-side validation capability. Only CandidateValidator can create it and it cannot be serialized. */
@JsonSerialize(using = CandidateValidationDecisionSerializer.class)
public final class CandidateValidationDecision {
    private final CandidateValidationResult result;

    CandidateValidationDecision(CandidateValidationResult result) {
        this.result = result;
    }

    public CandidateValidationResult result() {
        return result;
    }
}
