package com.yanban.core.agent.sandbox;

import com.fasterxml.jackson.annotation.JsonValue;

/** SHA-256 identity of authority-free Candidate content. */
public record CandidateFingerprint(@JsonValue String sha256) implements RejectsUnknownFields {
    public CandidateFingerprint {
        if (sha256 == null || !sha256.matches("(?i)^[a-f0-9]{64}$")) {
            throw new IllegalArgumentException("candidate fingerprint must be a SHA-256 digest");
        }
        sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
    }
}
