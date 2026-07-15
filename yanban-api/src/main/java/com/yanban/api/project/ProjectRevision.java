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
@Table(name = "project_revisions")
public class ProjectRevision {
    public enum SourceType { UPLOAD, APPLICATION }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "project_version", nullable = false, length = 64) private String projectVersion;
    @Column(name = "object_prefix", nullable = false, length = 1024) private String objectPrefix;
    @Column(name = "file_count", nullable = false) private int fileCount;
    @Column(name = "total_bytes", nullable = false) private long totalBytes;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType;
    @Column(name = "source_operation_id") private Long sourceOperationId;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProjectRevision() { }

    public ProjectRevision(Long projectId, Long userId, String projectVersion, String objectPrefix,
                           int fileCount, long totalBytes, SourceType sourceType, Long sourceOperationId) {
        if (projectId == null || projectId < 1 || userId == null || userId < 1
                || projectVersion == null || !projectVersion.matches("[0-9a-f]{64}")
                || objectPrefix == null || objectPrefix.isBlank() || fileCount < 0 || totalBytes < 0
                || sourceType == null) {
            throw new IllegalArgumentException("Project revision is incomplete");
        }
        this.projectId = projectId;
        this.userId = userId;
        this.projectVersion = projectVersion;
        this.objectPrefix = objectPrefix;
        this.fileCount = fileCount;
        this.totalBytes = totalBytes;
        this.sourceType = sourceType;
        this.sourceOperationId = sourceOperationId;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getUserId() { return userId; }
    public String getProjectVersion() { return projectVersion; }
    public String getObjectPrefix() { return objectPrefix; }
    public int getFileCount() { return fileCount; }
    public long getTotalBytes() { return totalBytes; }
    public SourceType getSourceType() { return sourceType; }
    public Long getSourceOperationId() { return sourceOperationId; }
    public Instant getCreatedAt() { return createdAt; }
    void bindSourceOperation(Long operationId) { this.sourceOperationId = operationId; }
}
