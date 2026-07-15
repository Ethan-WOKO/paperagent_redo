CREATE TABLE project_revisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    project_version VARCHAR(64) NOT NULL,
    object_prefix VARCHAR(1024) NOT NULL,
    file_count INT NOT NULL,
    total_bytes BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_operation_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_revisions_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    INDEX idx_project_revisions_owner_history (user_id, project_id, created_at, id)
);

CREATE TABLE project_revision_operations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    base_revision_id BIGINT NULL,
    base_version VARCHAR(64) NOT NULL,
    result_revision_id BIGINT NULL,
    result_version VARCHAR(64) NULL,
    candidate_artifact_id BIGINT NULL,
    candidate_fingerprint VARCHAR(64) NULL,
    accepted_change_indexes TEXT NOT NULL,
    rejected_change_indexes TEXT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_project_revision_operations_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uk_project_revision_operation_idempotency UNIQUE (user_id, project_id, idempotency_key),
    INDEX idx_project_revision_operations_audit (user_id, project_id, created_at, id)
);

ALTER TABLE projects
    ADD COLUMN current_revision_id BIGINT NULL AFTER index_version,
    ADD COLUMN revision_lock BIGINT NOT NULL DEFAULT 0 AFTER current_revision_id;
