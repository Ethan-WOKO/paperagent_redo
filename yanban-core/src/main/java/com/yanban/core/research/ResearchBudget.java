package com.yanban.core.research;

/** Deterministic resource ceiling for one read-only tool call. */
public record ResearchBudget(int maxInputPaths, int maxOutputItems, int maxEvidenceRefs, long maxBytesInspected) {
    public ResearchBudget {
        if (maxInputPaths < 1 || maxOutputItems < 1 || maxEvidenceRefs < 1 || maxBytesInspected < 1) {
            throw new IllegalArgumentException("research budget limits must be positive");
        }
    }

    public void validate(ResearchBudgetUsage usage) {
        if (usage == null || usage.inputPaths() > maxInputPaths || usage.outputItems() > maxOutputItems
                || usage.evidenceRefs() > maxEvidenceRefs || usage.bytesInspected() > maxBytesInspected) {
            throw new ResearchContractException(ResearchToolErrorCode.BUDGET_EXCEEDED,
                    "research tool result exceeds its declared budget");
        }
    }
}
