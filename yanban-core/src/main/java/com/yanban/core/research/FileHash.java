package com.yanban.core.research;

import com.fasterxml.jackson.annotation.JsonValue;

/** SHA-256 digest in its portable, ordinary projection form. */
public record FileHash(@JsonValue String sha256) {
    public FileHash {
        if (sha256 == null || !sha256.matches("(?i)^[a-f0-9]{64}$")) {
            throw new IllegalArgumentException("file hash must be a 64-character SHA-256 hex digest");
        }
        sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
    }
}
