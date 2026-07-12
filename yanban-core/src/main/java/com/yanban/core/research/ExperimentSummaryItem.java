package com.yanban.core.research;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Experiment configuration, metric, report, or result summary. */
public record ExperimentSummaryItem(String assetType, @JsonInclude(JsonInclude.Include.NON_NULL) String metricName, String value,
                                    UntrustedResearchContent content) implements ResearchToolItem {
    public ExperimentSummaryItem {
        require(assetType, "assetType"); optional(metricName, "metricName"); require(value, "value");
        if (content == null) throw new IllegalArgumentException("content must not be null");
    }

    @Override public ResearchToolItemType itemType() { return ResearchToolItemType.EXPERIMENT_SUMMARY; }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }

    private static void optional(String value, String field) {
        if (value != null && value.isBlank()) throw new IllegalArgumentException(field + " must be null or non-blank");
    }
}
