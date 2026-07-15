package com.yanban.api.project;

import java.time.Instant;
import java.util.List;

public record ProjectRevisionOperationResponse(
        Long operationId,
        ProjectRevisionOperation.Type operationType,
        ProjectRevisionOperation.Outcome outcome,
        Long baseRevisionId,
        String baseVersion,
        Long resultRevisionId,
        String resultVersion,
        Long candidateArtifactId,
        String candidateFingerprint,
        List<Integer> acceptedChangeIndexes,
        List<Integer> rejectedChangeIndexes,
        Instant completedAt
) { }
