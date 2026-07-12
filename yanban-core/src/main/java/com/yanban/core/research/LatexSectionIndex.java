package com.yanban.core.research;

/** Contract-only entry for a LaTex section. */
public record LatexSectionIndex(String sectionId, String title, int level, IndexedProvenance provenance) {
    public LatexSectionIndex {
        if (sectionId == null || sectionId.isBlank() || title == null || level < 1 || provenance == null) {
            throw new IllegalArgumentException("LaTeX section index entry is incomplete");
        }
    }
}
