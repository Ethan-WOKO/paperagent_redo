package com.yanban.core.research;

import java.util.List;

/** Audit-safe view: it intentionally omits user/project ids, absolute paths, capabilities, and source content. */
public record ResearchAuditProjection(String toolName, String deduplicationKey, ResearchToolResultState state,
                                      ResearchToolErrorCode errorCode, boolean retryable,
                                      List<ResearchEvidenceRef> evidenceRefs, ResearchBudgetUsage budgetUsage) {
    public ResearchAuditProjection {
        if (toolName == null || toolName.isBlank() || deduplicationKey == null || deduplicationKey.isBlank()
                || state == null || budgetUsage == null) {
            throw new IllegalArgumentException("audit projection is incomplete");
        }
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        retryable = errorCode != null && errorCode.retryable();
    }
}
