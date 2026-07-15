package com.yanban.api.agent;

import java.time.Instant;

public record TaskStatusResponse(
        String taskType,
        Long taskId,
        String status,
        String currentStage,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt,
        Integer progressPercent,
        String errorCode,
        String errorMessage,
        String cancellationReason,
        boolean partialResultAvailable,
        int completedArtifactCount,
        int partialArtifactCount,
        Long lastEventId,
        String lastEventType,
        String lastEventMessage,
        Instant lastEventAt,
        boolean terminal,
        boolean cancellable,
        String phase,
        String outcome
) {
    public TaskStatusResponse(String taskType, Long taskId, String status, String currentStage,
                              Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt,
                              Integer progressPercent, String errorCode, String errorMessage,
                              String cancellationReason, boolean partialResultAvailable,
                              int completedArtifactCount, int partialArtifactCount, Long lastEventId,
                              String lastEventType, String lastEventMessage, Instant lastEventAt,
                              boolean terminal, boolean cancellable) {
        this(taskType, taskId, status, currentStage, createdAt, updatedAt, startedAt, finishedAt,
                progressPercent, errorCode, errorMessage, cancellationReason, partialResultAvailable,
                completedArtifactCount, partialArtifactCount, lastEventId, lastEventType,
                lastEventMessage, lastEventAt, terminal, cancellable, null, null);
    }
}
