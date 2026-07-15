package com.yanban.api.project;

import java.util.List;

public record ApplyCandidateRequest(List<Integer> acceptedChangeIndexes) {
    public ApplyCandidateRequest {
        acceptedChangeIndexes = acceptedChangeIndexes == null ? List.of() : List.copyOf(acceptedChangeIndexes);
    }
}
