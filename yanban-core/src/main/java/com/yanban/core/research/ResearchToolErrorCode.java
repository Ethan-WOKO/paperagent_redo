package com.yanban.core.research;

/** Stable error taxonomy for the future executors; no executor is supplied by this contract. */
public enum ResearchToolErrorCode {
    INVALID_ARGUMENT(false),
    PATH_OUTSIDE_PROJECT(false),
    PROJECT_SCOPE_UNAVAILABLE(false),
    BUDGET_EXCEEDED(false),
    UNSUPPORTED_FILE_TYPE(false),
    PARSER_FAILURE(false),
    PARTIAL_RESULT(false),
    INDEX_STALE(false),
    RESULT_TRUNCATED(false),
    TRANSIENT_PROJECT_IO(true),
    INTERNAL_CONTRACT_FAILURE(false);

    private final boolean retryable;

    ResearchToolErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
