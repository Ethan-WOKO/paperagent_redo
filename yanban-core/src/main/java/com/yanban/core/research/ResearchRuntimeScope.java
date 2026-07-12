package com.yanban.core.research;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Set;

/**
 * Server-only authority injected by a future Runtime after policy resolution. This type is never
 * embedded in a ToolDefinition schema, ResearchEvidenceRef, index entry, audit projection, or model result.
 */
@JsonSerialize(using = ResearchRuntimeScopeSerializer.class)
public record ResearchRuntimeScope(long trustedProjectId, long trustedUserId, Set<String> trustedCapabilities,
                                   ProjectVersionRef projectVersion) {
    public ResearchRuntimeScope {
        if (trustedProjectId < 1 || trustedUserId < 1 || projectVersion == null) {
            throw new IllegalArgumentException("research runtime scope must be server-attested");
        }
        trustedCapabilities = trustedCapabilities == null ? Set.of() : Set.copyOf(trustedCapabilities);
    }

    public void requireCapability(String capability) {
        if (capability == null || !trustedCapabilities.contains(capability)) {
            throw new ResearchContractException(ResearchToolErrorCode.PROJECT_SCOPE_UNAVAILABLE,
                    "research capability is not present in the runtime scope");
        }
    }
}
