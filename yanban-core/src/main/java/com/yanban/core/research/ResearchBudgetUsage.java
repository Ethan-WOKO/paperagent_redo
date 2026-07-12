package com.yanban.core.research;

public record ResearchBudgetUsage(int inputPaths, int outputItems, int evidenceRefs, long bytesInspected) {
    public ResearchBudgetUsage {
        if (inputPaths < 0 || outputItems < 0 || evidenceRefs < 0 || bytesInspected < 0) {
            throw new IllegalArgumentException("research budget usage must not be negative");
        }
    }
}
