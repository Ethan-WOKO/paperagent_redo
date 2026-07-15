package com.yanban.api.project;

import java.time.Instant;

public record ProjectRevisionResponse(Long id, String projectVersion, boolean current, int fileCount,
                                      long totalBytes, ProjectRevision.SourceType sourceType, Instant createdAt) {
    static ProjectRevisionResponse from(ProjectRevision revision, Long currentRevisionId) {
        return new ProjectRevisionResponse(revision.getId(), revision.getProjectVersion(),
                revision.getId().equals(currentRevisionId), revision.getFileCount(), revision.getTotalBytes(),
                revision.getSourceType(), revision.getCreatedAt());
    }
}
