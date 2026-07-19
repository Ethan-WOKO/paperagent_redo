CREATE TABLE sandbox_executions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    execution_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    api_fence BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    worker_owner VARCHAR(128) NULL,
    worker_token VARCHAR(32) NULL,
    worker_fence BIGINT NOT NULL DEFAULT 0,
    lease_expires_at DATETIME(6) NULL,
    sandbox_name VARCHAR(128) NOT NULL,
    request_json LONGTEXT NULL,
    checkpoint_json LONGTEXT NULL,
    receipt_digest VARCHAR(64) NULL,
    receipt_json LONGTEXT NULL,
    error_code VARCHAR(64) NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sandbox_execution_id (execution_id),
    UNIQUE KEY uk_sandbox_execution_idempotency (idempotency_key),
    INDEX idx_sandbox_execution_claim (status, lease_expires_at)
);

CREATE TABLE sandbox_concurrency_slot (
    slot_id TINYINT NOT NULL,
    PRIMARY KEY (slot_id)
);
INSERT INTO sandbox_concurrency_slot(slot_id) VALUES (1);
