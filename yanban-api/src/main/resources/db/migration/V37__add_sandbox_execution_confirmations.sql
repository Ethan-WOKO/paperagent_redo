CREATE TABLE sandbox_execution_confirmations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    project_version VARCHAR(64) NOT NULL,
    sandbox_step_set_digest VARCHAR(64) NOT NULL,
    confirmation_key VARCHAR(128) NOT NULL,
    confirmed_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sandbox_confirmation_plan (plan_id),
    UNIQUE KEY uk_sandbox_confirmation_key (confirmation_key),
    INDEX idx_sandbox_confirmation_scope (user_id, project_id, plan_id)
);
