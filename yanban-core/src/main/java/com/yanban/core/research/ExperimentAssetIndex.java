package com.yanban.core.research;

/** Configuration, metric table, report, or result-file summary. */
public record ExperimentAssetIndex(String assetType, String summary, IndexedProvenance provenance) {
    public ExperimentAssetIndex {
        if (assetType == null || assetType.isBlank() || summary == null || provenance == null) {
            throw new IllegalArgumentException("experiment asset index entry is incomplete");
        }
    }
}
