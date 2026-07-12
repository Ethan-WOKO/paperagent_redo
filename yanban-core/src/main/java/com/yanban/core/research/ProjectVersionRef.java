package com.yanban.core.research;

import com.fasterxml.jackson.annotation.JsonValue;

/** Server-attested Project version reference. A Project id is deliberately not projected here. */
public record ProjectVersionRef(@JsonValue String value) {
    public static final String SHA256_PATTERN = "^[a-f0-9]{64}$";
    public static final String NAMESPACED_TOKEN_PATTERN = "^[A-Za-z][A-Za-z0-9._-]{1,63}:[A-Za-z0-9][A-Za-z0-9._@+-]{0,95}$";

    public ProjectVersionRef {
        if (value != null && value.matches("(?i)^[a-f0-9]{64}$")) {
            value = value.toLowerCase(java.util.Locale.ROOT);
        } else if (value == null || !value.matches(NAMESPACED_TOKEN_PATTERN)
                || value.regionMatches(true, 0, "file:", 0, 5)
                || value.regionMatches(true, 0, "http:", 0, 5)
                || value.regionMatches(true, 0, "https:", 0, 6)
                || value.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("project version must be a portable opaque version token");
        }
    }
}
