package com.yanban.core.research;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result projection for a future executor. Its JSON shape is deliberately the shared output
 * schema shape; budget and retryability stay server-side/audit-only.
 */
public record ResearchToolOutcome(ResearchToolResultState status, List<ResearchToolItem> items,
                                  List<ResearchEvidenceRef> evidenceRefs,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) ResearchToolErrorCode errorCode,
                                  @JsonIgnore ResearchBudgetUsage budgetUsage) {
    public ResearchToolOutcome {
        if (status == null || budgetUsage == null) {
            throw new IllegalArgumentException("research outcome requires status and budget usage");
        }
        items = items == null ? List.of() : List.copyOf(items);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        switch (status) {
            case COMPLETE -> requireItemsAndNoError(items, errorCode, "complete");
            case EMPTY -> {
                if (!items.isEmpty() || !evidenceRefs.isEmpty() || errorCode != null) {
                    throw new IllegalArgumentException("empty outcomes must not carry items, evidence, or an error");
                }
            }
            case PARTIAL -> {
                requireItemsAndError(items, errorCode, ResearchToolErrorCode.PARTIAL_RESULT, "partial");
                if (evidenceRefs.isEmpty()) throw new IllegalArgumentException("partial outcomes require evidence");
            }
            case TRUNCATED -> {
                requireItemsAndError(items, errorCode, ResearchToolErrorCode.RESULT_TRUNCATED, "truncated");
                if (evidenceRefs.isEmpty()) throw new IllegalArgumentException("truncated outcomes require evidence");
            }
            case PARSE_FAILED -> {
                if (!items.isEmpty() || !evidenceRefs.isEmpty() || errorCode != ResearchToolErrorCode.PARSER_FAILURE) {
                    throw new IllegalArgumentException("parse failures must contain no partial result and use PARSER_FAILURE");
                }
            }
        }
    }

    @JsonProperty("partial")
    public boolean partial() {
        return status == ResearchToolResultState.PARTIAL || status == ResearchToolResultState.TRUNCATED
                || status == ResearchToolResultState.PARSE_FAILED;
    }

    @JsonProperty("truncated")
    public boolean truncated() {
        return status == ResearchToolResultState.TRUNCATED;
    }

    @JsonProperty("parseFailed")
    public boolean parseFailed() {
        return status == ResearchToolResultState.PARSE_FAILED;
    }

    @JsonIgnore
    public boolean retryable() {
        return errorCode != null && errorCode.retryable();
    }

    private static void requireItemsAndNoError(List<ResearchToolItem> items, ResearchToolErrorCode errorCode, String state) {
        if (items.isEmpty() || errorCode != null) {
            throw new IllegalArgumentException(state + " outcomes require items and no error");
        }
    }

    private static void requireItemsAndError(List<ResearchToolItem> items, ResearchToolErrorCode errorCode,
                                             ResearchToolErrorCode expected, String state) {
        if (items.isEmpty() || errorCode != expected) {
            throw new IllegalArgumentException(state + " outcomes require items and " + expected);
        }
    }
}
