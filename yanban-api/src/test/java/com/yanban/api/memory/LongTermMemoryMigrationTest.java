package com.yanban.api.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import com.yanban.core.agent.AgentLongTermMemory;
import com.yanban.core.agent.AgentLongTermMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:long_term_memory_migration_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "yanban.jwt.secret=test_secret_123456789012345678901234567890"
})
class LongTermMemoryMigrationTest {

    private static final Set<String> EXPECTED_COLUMNS = Set.of(
            "user_id",
            "project_id",
            "scope",
            "memory_type",
            "content",
            "tags_json",
            "source_type",
            "source_ref_id",
            "confidence",
            "status",
            "confirmation_status",
            "confirmed_at",
            "confirmed_source",
            "provenance_type",
            "provenance_ref",
            "project_version",
            "expires_at",
            "invalidated_at",
            "invalidation_reason",
            "supersedes_memory_id",
            "superseded_by_memory_id",
            "created_at",
            "updated_at",
            "deleted_at"
    );

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AgentLongTermMemoryRepository memories;

    @Test
    void migrationCreatesLongTermMemoryColumnsAndIndexes() throws SQLException {
        assertThat(columns("agent_long_term_memories")).containsAll(EXPECTED_COLUMNS);
        Set<String> indexes = indexes("agent_long_term_memories");
        assertThat(indexes).contains(
                "idx_agent_long_term_memories_user_status_updated",
                "idx_agent_long_term_memories_user_scope_type",
                "idx_agent_long_term_memories_project_status",
                "idx_ltm_user_governed",
                "idx_ltm_project_version_governed"
        );
    }

    @Test
    void canInsertCorrectAndSoftDeleteMemory() {
        jdbc.update("""
                INSERT INTO sys_users (username, password_hash)
                VALUES (?, ?)
                """, "memory-user", "hash");
        Long userId = jdbc.queryForObject("SELECT id FROM sys_users WHERE username = ?", Long.class, "memory-user");

        jdbc.update("""
                INSERT INTO agent_long_term_memories
                    (user_id, scope, memory_type, content, tags_json, source_type, confidence, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, "USER", "PREFERENCE", "User prefers concise prose.", "[\"style\"]",
                "USER_CONFIRMED", 0.85, "ACTIVE");
        Long firstId = jdbc.queryForObject("SELECT id FROM agent_long_term_memories WHERE user_id = ?", Long.class, userId);
        assertThat(jdbc.queryForObject("SELECT confirmation_status FROM agent_long_term_memories WHERE id = ?",
                String.class, firstId)).isEqualTo("UNCONFIRMED");

        jdbc.update("""
                INSERT INTO agent_long_term_memories
                    (user_id, project_id, scope, memory_type, content, source_type, confidence, status)
                VALUES (?, ?, 'PROJECT', 'FACT', ?, 'USER_CONFIRMED', 0.8, 'ACTIVE')
                """, userId, 77L, "Legacy unversioned project memory");
        assertThat(jdbc.queryForObject("""
                SELECT confirmation_status FROM agent_long_term_memories
                WHERE user_id = ? AND project_id = ?
                """, String.class, userId, 77L)).isEqualTo("UNCONFIRMED");

        jdbc.update("""
                INSERT INTO agent_long_term_memories
                    (user_id, scope, memory_type, content, source_type, source_ref_id, confidence, status, supersedes_memory_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, "USER", "PREFERENCE", "User prefers detailed prose.", "USER_CORRECTED",
                String.valueOf(firstId), 0.9, "ACTIVE", firstId);
        Long secondId = jdbc.queryForObject("""
                SELECT id FROM agent_long_term_memories
                WHERE user_id = ? AND supersedes_memory_id = ?
                """, Long.class, userId, firstId);

        jdbc.update("""
                UPDATE agent_long_term_memories
                SET status = ?, superseded_by_memory_id = ?
                WHERE id = ?
                """, "SUPERSEDED", secondId, firstId);
        jdbc.update("""
                UPDATE agent_long_term_memories
                SET status = ?, deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, "DELETED", secondId);

        Integer deletedCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM agent_long_term_memories
                WHERE user_id = ? AND status = ?
                """, Integer.class, userId, "DELETED");
        assertThat(deletedCount).isEqualTo(1);
    }

    @Test
    void repositoryQueriesOnlyGovernedUserAndExactProjectVersionRows() {
        jdbc.update("INSERT INTO sys_users (username, password_hash) VALUES (?, ?)", "governed-memory-user", "hash");
        Long userId = jdbc.queryForObject("SELECT id FROM sys_users WHERE username = ?", Long.class,
                "governed-memory-user");
        String currentVersion = "a".repeat(64);
        insertGoverned(userId, null, "USER", null, "Governed user memory", "CONFIRMED", null, null);
        insertGoverned(userId, null, "USER", null, "Unconfirmed user memory", "UNCONFIRMED", null, null);
        insertGoverned(userId, 7L, "PROJECT", currentVersion, "Current project memory", "CONFIRMED", null, null);
        insertGoverned(userId, 7L, "PROJECT", "b".repeat(64), "Old project memory", "CONFIRMED", null, null);
        insertGoverned(userId, 7L, "PROJECT", currentVersion, "Expired project memory", "CONFIRMED",
                Instant.now().minusSeconds(60), null);
        insertGoverned(userId, 7L, "PROJECT", currentVersion, "Invalidated project memory", "CONFIRMED", null,
                Instant.now().minusSeconds(60));

        List<AgentLongTermMemory> userRows = memories.findGovernedUserCandidates(
                userId, Instant.now(), PageRequest.of(0, 20));
        List<AgentLongTermMemory> projectRows = memories.findGovernedProjectCandidates(
                userId, 7L, currentVersion, Instant.now(), PageRequest.of(0, 20));

        assertThat(userRows).extracting(AgentLongTermMemory::getContent)
                .containsExactly("Governed user memory");
        assertThat(projectRows).extracting(AgentLongTermMemory::getContent)
                .containsExactly("Current project memory");
    }

    private void insertGoverned(Long userId,
                                Long projectId,
                                String scope,
                                String projectVersion,
                                String content,
                                String confirmationStatus,
                                Instant expiresAt,
                                Instant invalidatedAt) {
        jdbc.update("""
                INSERT INTO agent_long_term_memories
                    (user_id, project_id, scope, memory_type, content, source_type, confidence, status,
                     confirmation_status, confirmed_at, confirmed_source, provenance_type, provenance_ref,
                     project_version, expires_at, invalidated_at)
                VALUES (?, ?, ?, 'FACT', ?, 'USER_CONFIRMED', 0.9, 'ACTIVE', ?, CURRENT_TIMESTAMP,
                        'USER_ACTION', 'USER_MESSAGE', 'session:1:message:1', ?, ?, ?)
                """, userId, projectId, scope, content, confirmationStatus, projectVersion, expiresAt, invalidatedAt);
    }

    private Set<String> columns(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                ResultSet rs = connection.getMetaData().getColumns(null, null, tableName, null)) {
            Set<String> names = new java.util.HashSet<>();
            while (rs.next()) {
                names.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
            return names;
        }
    }

    private Set<String> indexes(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                ResultSet rs = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            Set<String> names = new java.util.HashSet<>();
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName != null) {
                    names.add(indexName.toLowerCase());
                }
            }
            return names;
        }
    }
}
