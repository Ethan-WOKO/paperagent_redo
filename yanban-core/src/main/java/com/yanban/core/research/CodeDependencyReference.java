package com.yanban.core.research;

/** Dependency edge between indexed code symbols or modules. */
public record CodeDependencyReference(String fromSymbolId, String toReference,
                                      IndexedProvenance provenance) {
    public CodeDependencyReference {
        if (fromSymbolId == null || fromSymbolId.isBlank() || toReference == null || toReference.isBlank()
                || provenance == null) {
            throw new IllegalArgumentException("code dependency reference is incomplete");
        }
    }
}
