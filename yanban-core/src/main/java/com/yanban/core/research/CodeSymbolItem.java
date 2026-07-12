package com.yanban.core.research;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Code symbol/entry point with an explicit dependency reference field. */
public record CodeSymbolItem(String kind, String qualifiedName, @JsonInclude(JsonInclude.Include.NON_NULL) String dependencyReference,
                             UntrustedResearchContent content) implements ResearchToolItem {
    public CodeSymbolItem {
        require(kind, "kind"); require(qualifiedName, "qualifiedName"); optional(dependencyReference, "dependencyReference");
        if (content == null) throw new IllegalArgumentException("content must not be null");
    }

    @Override public ResearchToolItemType itemType() { return ResearchToolItemType.CODE_SYMBOL; }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }

    private static void optional(String value, String field) {
        if (value != null && value.isBlank()) throw new IllegalArgumentException(field + " must be null or non-blank");
    }
}
