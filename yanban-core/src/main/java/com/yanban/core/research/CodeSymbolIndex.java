package com.yanban.core.research;

import java.util.List;

/** Code symbol relation; names and signatures are untrusted Project content. */
public record CodeSymbolIndex(String symbolId, String kind, String qualifiedName, List<String> parameterNames,
                              IndexedProvenance provenance) {
    public CodeSymbolIndex {
        if (symbolId == null || symbolId.isBlank() || kind == null || kind.isBlank()
                || qualifiedName == null || qualifiedName.isBlank() || provenance == null) {
            throw new IllegalArgumentException("code symbol index entry is incomplete");
        }
        parameterNames = parameterNames == null ? List.of() : List.copyOf(parameterNames);
    }
}
