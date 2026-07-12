package com.yanban.core.research;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Section, formula-reference, citation, or float entry extracted from LaTex source. */
public record LatexOutlineItem(String kind, @JsonInclude(JsonInclude.Include.NON_NULL) String identifier,
                               String detail, UntrustedResearchContent content)
        implements ResearchToolItem {
    public LatexOutlineItem {
        require(kind, "kind"); optional(identifier, "identifier"); require(detail, "detail");
        if (content == null) throw new IllegalArgumentException("content must not be null");
    }

    @Override public ResearchToolItemType itemType() { return ResearchToolItemType.LATEX_OUTLINE; }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }

    private static void optional(String value, String field) {
        if (value != null && value.isBlank()) throw new IllegalArgumentException(field + " must be null or non-blank");
    }
}
