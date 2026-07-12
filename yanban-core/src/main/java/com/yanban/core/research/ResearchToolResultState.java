package com.yanban.core.research;

/** A parser failure or truncation is observable rather than silently treated as complete. */
public enum ResearchToolResultState {
    COMPLETE,
    EMPTY,
    PARTIAL,
    TRUNCATED,
    PARSE_FAILED
}
