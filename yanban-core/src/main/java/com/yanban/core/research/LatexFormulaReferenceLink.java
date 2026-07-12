package com.yanban.core.research;

/** Formula label/reference relation, with each endpoint anchored in the same Project version. */
public record LatexFormulaReferenceLink(String formulaLabel, String referenceLabel,
                                        IndexedProvenance formulaProvenance,
                                        IndexedProvenance referenceProvenance) {
    public LatexFormulaReferenceLink {
        if (formulaLabel == null || formulaLabel.isBlank() || referenceLabel == null || referenceLabel.isBlank()
                || formulaProvenance == null || referenceProvenance == null) {
            throw new IllegalArgumentException("formula/reference link is incomplete");
        }
    }
}
