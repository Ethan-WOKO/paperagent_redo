package com.yanban.core.research;

import com.fasterxml.jackson.annotation.JsonValue;

/** Portable parser identity/version token permitted in index, evidence, and audit projections. */
public record ParserVersionRef(@JsonValue String value) {
    public static final String PATTERN = "^[A-Za-z][A-Za-z0-9._-]{0,63}(?:@[A-Za-z0-9][A-Za-z0-9._+-]{0,95})?$";

    public ParserVersionRef {
        if (value == null || !value.matches(PATTERN)) {
            throw new IllegalArgumentException("parser version must be a portable identity/version token");
        }
    }
}
