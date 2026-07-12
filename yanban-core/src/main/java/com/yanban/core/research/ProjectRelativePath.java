package com.yanban.core.research;

import com.fasterxml.jackson.annotation.JsonValue;

/** A normalized path inside a Project; it never represents a host filesystem path. */
public record ProjectRelativePath(@JsonValue String value) {

    public ProjectRelativePath {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("relative path must not be blank");
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException("path must already be normalized without surrounding whitespace");
        }
        if (value.indexOf('\\') >= 0 || value.indexOf(':') >= 0 || value.startsWith("/") || value.matches("^[A-Za-z]:.*")
                || value.contains("//")) {
            throw new IllegalArgumentException("path must be normalized and Project-relative");
        }
        if (value.chars().anyMatch(character -> character >= 0 && character < 32)) {
            throw new IllegalArgumentException("path must not contain control characters");
        }
        for (String segment : value.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("path traversal is not allowed");
            }
        }
    }

    public static ProjectRelativePath of(String value) {
        return new ProjectRelativePath(value);
    }
}
