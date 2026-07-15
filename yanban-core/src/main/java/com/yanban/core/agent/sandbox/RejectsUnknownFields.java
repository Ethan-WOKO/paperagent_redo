package com.yanban.core.agent.sandbox;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Rejects schema drift even when a surrounding ObjectMapper ignores unknown properties. */
interface RejectsUnknownFields {
    @JsonAnySetter
    default void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("unknown sandbox contract field: " + name);
    }
}
