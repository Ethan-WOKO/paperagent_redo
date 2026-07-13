ALTER TABLE agent_sessions
    ADD COLUMN scope VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE' AFTER rag_disabled,
    ADD COLUMN project_id BIGINT NULL AFTER scope;

CREATE INDEX idx_agent_sessions_user_scope_updated ON agent_sessions(user_id, scope, updated_at);
CREATE INDEX idx_agent_sessions_user_project_updated ON agent_sessions(user_id, project_id, updated_at);

ALTER TABLE agent_sessions
    ADD CONSTRAINT fk_agent_sessions_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;
