package com.yanban.paper.literature;

import com.yanban.paper.domain.LiteratureSearchTask;
import com.yanban.paper.domain.LiteratureSearchTaskRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LiteratureSearchTaskService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_CANCEL_REQUESTED = "CANCEL_REQUESTED";
    public static final String STATUS_CANCELLING = "CANCELLING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private static final Set<String> TERMINAL_STATUSES = Set.of(STATUS_COMPLETED, STATUS_FAILED, STATUS_CANCELLED);
    private static final Set<String> CANCEL_STATUSES = Set.of(STATUS_CANCEL_REQUESTED, STATUS_CANCELLING, STATUS_CANCELLED);

    private final LiteratureSearchTaskRepository tasks;
    private final LiteratureSearchTaskPublisher publisher;

    public LiteratureSearchTaskService(LiteratureSearchTaskRepository tasks,
                                       ObjectProvider<LiteratureSearchTaskPublisher> publisherProvider) {
        this.tasks = tasks;
        this.publisher = publisherProvider == null ? null : publisherProvider.getIfAvailable();
    }

    @Transactional
    public TaskStartResult createTask(Long userId, LiteratureSearchTaskRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少当前用户上下文");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request 不能为空");
        }
        String query = normalizeQuery(request.query());
        if (!StringUtils.hasText(query)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query 不能为空");
        }
        int topK = clampTopK(request.topK());
        Integer yearFrom = request.yearFrom();
        boolean includeBibtex = request.includeBibtex() == null || request.includeBibtex();
        String clientRequestId = clientRequestId(request.clientRequestId());
        String idempotencyKey = idempotencyKey(userId, query, topK, yearFrom, includeBibtex, clientRequestId);
        return tasks.findByIdempotencyKey(idempotencyKey)
                .map(task -> new TaskStartResult(task, true))
                .orElseGet(() -> {
                    LiteratureSearchTask saved = tasks.save(new LiteratureSearchTask(
                            userId,
                            request.projectId(),
                            query,
                            query.toLowerCase(Locale.ROOT),
                            topK,
                            yearFrom,
                            includeBibtex,
                            STATUS_PENDING,
                            "QUEUED",
                            clientRequestId,
                            idempotencyKey
                    ));
                    publishAfterCommit(saved);
                    return new TaskStartResult(saved, false);
                });
    }

    @Transactional(readOnly = true)
    public LiteratureSearchTask getTask(Long userId, Long taskId) {
        return ownedTask(userId, taskId);
    }

    @Transactional
    public LiteratureSearchTask requestCancel(Long userId, Long taskId, String cancelReason) {
        LiteratureSearchTask task = ownedTask(userId, taskId);
        if (TERMINAL_STATUSES.contains(task.getStatus())) {
            return task;
        }
        task.setStatus(STATUS_CANCEL_REQUESTED);
        task.setCurrentStage("CANCEL_REQUESTED");
        task.setCancelReason(trimToNull(cancelReason));
        return tasks.save(task);
    }

    @Transactional
    public LiteratureSearchTask saveResult(Long userId,
                                           Long taskId,
                                           String resultJson,
                                           Integer rawCandidateCount,
                                           Integer uniqueCandidateCount,
                                           Integer sourceAttempts,
        String sourceFailuresJson) {
        LiteratureSearchTask task = ownedTask(userId, taskId);
        if (CANCEL_STATUSES.contains(task.getStatus())) {
            return markCancelled(userId, taskId);
        }
        task.setResultJson(resultJson);
        task.setRawCandidateCount(rawCandidateCount);
        task.setUniqueCandidateCount(uniqueCandidateCount);
        task.setSourceAttempts(sourceAttempts);
        task.setSourceFailuresJson(sourceFailuresJson);
        task.setStatus(STATUS_COMPLETED);
        task.setCurrentStage("COMPLETE");
        task.setFinishedAt(Instant.now());
        return tasks.save(task);
    }

    @Transactional
    public Optional<LiteratureSearchTask> claimForRun(Long taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        Optional<LiteratureSearchTask> taskOpt = tasks.findById(taskId);
        if (taskOpt.isEmpty()) {
            return Optional.empty();
        }
        LiteratureSearchTask task = taskOpt.get();
        if (STATUS_PENDING.equals(task.getStatus())) {
            task.setStatus(STATUS_RUNNING);
            task.setCurrentStage("SEARCHING");
            task.setStartedAt(Instant.now());
            return Optional.of(tasks.save(task));
        }
        if (STATUS_CANCEL_REQUESTED.equals(task.getStatus()) || STATUS_CANCELLING.equals(task.getStatus())) {
            task.setStatus(STATUS_CANCELLED);
            task.setCurrentStage("CANCELLED");
            task.setFinishedAt(Instant.now());
            tasks.save(task);
            return Optional.empty();
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public boolean isCancellationRequested(Long userId, Long taskId) {
        return tasks.findByIdAndUserId(taskId, userId)
                .map(task -> CANCEL_STATUSES.contains(task.getStatus()))
                .orElse(false);
    }

    @Transactional
    public LiteratureSearchTask markCancelled(Long userId, Long taskId) {
        LiteratureSearchTask task = ownedTask(userId, taskId);
        task.setStatus(STATUS_CANCELLED);
        task.setCurrentStage("CANCELLED");
        task.setFinishedAt(Instant.now());
        return tasks.save(task);
    }

    @Transactional
    public LiteratureSearchTask markFailed(Long userId, Long taskId, String errorMessage) {
        LiteratureSearchTask task = ownedTask(userId, taskId);
        if (CANCEL_STATUSES.contains(task.getStatus())) {
            return markCancelled(userId, taskId);
        }
        task.setStatus(STATUS_FAILED);
        task.setCurrentStage("FAILED");
        task.setErrorMessage(trimToLength(errorMessage, 1000));
        task.setFinishedAt(Instant.now());
        return tasks.save(task);
    }

    public boolean isTerminal(String status) {
        return TERMINAL_STATUSES.contains(status);
    }

    private LiteratureSearchTask ownedTask(Long userId, Long taskId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少当前用户上下文");
        }
        if (taskId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId 不能为空");
        }
        return tasks.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文献检索任务不存在或不可访问"));
    }

    private int clampTopK(Integer topK) {
        int value = topK == null ? 8 : topK;
        return Math.min(Math.max(value, 1), 20);
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.replaceAll("\\s+", " ").trim();
    }

    private String clientRequestId(String value) {
        return StringUtils.hasText(value) ? value.trim() : UUID.randomUUID().toString();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String idempotencyKey(Long userId, String query, int topK, Integer yearFrom, boolean includeBibtex, String clientRequestId) {
        String raw = userId + "|LITERATURE_SEARCH|" + query.toLowerCase(Locale.ROOT) + "|" + topK + "|"
                + (yearFrom == null ? "" : yearFrom) + "|" + includeBibtex + "|" + clientRequestId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build literature task idempotency key", ex);
        }
    }

    private void publishAfterCommit(LiteratureSearchTask task) {
        if (publisher == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publishTaskCreated(task);
                }
            });
            return;
        }
        publisher.publishTaskCreated(task);
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public record TaskStartResult(LiteratureSearchTask task, boolean idempotent) {
    }
}
