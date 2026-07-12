package com.yanban.core.research;

import java.util.List;

/** BibTeX entry and its citation-use references; field text is untrusted Project data. */
public record BibEntryIndex(String citationKey, List<String> entryFields, List<IndexedProvenance> citationUses,
                            IndexedProvenance provenance) {
    public BibEntryIndex {
        if (citationKey == null || citationKey.isBlank() || provenance == null) {
            throw new IllegalArgumentException("Bib entry index requires citation key and provenance");
        }
        entryFields = entryFields == null ? List.of() : List.copyOf(entryFields);
        citationUses = citationUses == null ? List.of() : List.copyOf(citationUses);
    }
}
