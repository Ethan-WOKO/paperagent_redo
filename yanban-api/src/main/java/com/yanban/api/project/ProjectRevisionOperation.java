package com.yanban.api.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "project_revision_operations")
public class ProjectRevisionOperation {
    public enum Type { APPLICATION, ROLLBACK }
    public enum Outcome { STARTED, SUCCEEDED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(name = "operation_type", nullable = false, length = 32) private Type operationType;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "base_revision_id") private Long baseRevisionId;
    @Column(name = "base_version", nullable = false, length = 64) private String baseVersion;
    @Column(name = "result_revision_id") private Long resultRevisionId;
    @Column(name = "result_version", length = 64) private String resultVersion;
    @Column(name = "candidate_artifact_id") private Long candidateArtifactId;
    @Column(name = "candidate_fingerprint", length = 64) private String candidateFingerprint;
    @Column(name = "accepted_change_indexes", nullable = false, columnDefinition = "TEXT") private String acceptedChangeIndexes;
    @Column(name = "rejected_change_indexes", nullable = false, columnDefinition = "TEXT") private String rejectedChangeIndexes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Outcome outcome;
    @Column(name = "error_code", length = 64) private String errorCode;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected ProjectRevisionOperation() { }

    public ProjectRevisionOperation(Long projectId, Long userId, Type type, String idempotencyKey,
                                    String requestHash, Long baseRevisionId, String baseVersion,
                                    Long candidateArtifactId, String candidateFingerprint,
                                    String accepted, String rejected) {
        this.projectId = projectId; this.userId = userId; this.operationType = type;
        this.idempotencyKey = idempotencyKey; this.requestHash = requestHash;
        this.baseRevisionId = baseRevisionId; this.baseVersion = baseVersion;
        this.candidateArtifactId = candidateArtifactId; this.candidateFingerprint = candidateFingerprint;
        this.acceptedChangeIndexes = accepted; this.rejectedChangeIndexes = rejected;
        this.outcome = Outcome.STARTED;
    }

    public void succeed(Long revisionId, String version) {
        resultRevisionId = revisionId; resultVersion = version; outcome = Outcome.SUCCEEDED;
        errorCode = null; completedAt = Instant.now();
    }
    public void fail(String code) { outcome = Outcome.FAILED; errorCode = code; completedAt = Instant.now(); }
    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getUserId() { return userId; }
    public Type getOperationType() { return operationType; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Long getBaseRevisionId() { return baseRevisionId; }
    public String getBaseVersion() { return baseVersion; }
    public Long getResultRevisionId() { return resultRevisionId; }
    public String getResultVersion() { return resultVersion; }
    public Long getCandidateArtifactId() { return candidateArtifactId; }
    public String getCandidateFingerprint() { return candidateFingerprint; }
    public String getAcceptedChangeIndexes() { return acceptedChangeIndexes; }
    public String getRejectedChangeIndexes() { return rejectedChangeIndexes; }
    public Outcome getOutcome() { return outcome; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
