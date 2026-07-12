package com.yanban.core.research;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Stable BibTeX audit issue: key, issue classification, and detail. */
public record BibtexAuditItem(String issue, @JsonInclude(JsonInclude.Include.NON_NULL) String citationKey,
                              String detail, UntrustedResearchContent content)
        implements ResearchToolItem {
    public BibtexAuditItem {
        require(issue, "issue"); optional(citationKey, "citationKey"); require(detail, "detail");
        if (content == null) throw new IllegalArgumentException("content must not be null");
    }

    @Override public ResearchToolItemType itemType() { return ResearchToolItemType.BIBTEX_AUDIT; }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }

    private static void optional(String value, String field) {
        if (value != null && value.isBlank()) throw new IllegalArgumentException(field + " must be null or non-blank");
    }
}
