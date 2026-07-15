package com.yanban.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:project_revision_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "yanban.jwt.secret=test_secret_123456789012345678901234567890"
})
class ProjectRevisionMigrationTest {
    @Autowired DataSource dataSource;

    @Test
    void v34CreatesRevisionApplicationAndCurrentPointerSchema() throws Exception {
        assertThat(columns("projects")).contains("current_revision_id", "revision_lock");
        assertThat(columns("project_revisions")).contains("project_version", "object_prefix", "source_type");
        assertThat(columns("project_revision_operations")).contains("idempotency_key", "request_hash",
                "candidate_fingerprint", "accepted_change_indexes", "rejected_change_indexes", "outcome");
        assertThat(indexes("project_revision_operations"))
                .anyMatch(name -> name.startsWith("uk_project_revision_operation_idempotency"));
    }

    private Set<String> columns(String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet rows = connection.getMetaData().getColumns(null, null, table, null)) {
            Set<String> values = new HashSet<>();
            while (rows.next()) values.add(rows.getString("COLUMN_NAME").toLowerCase());
            return values;
        }
    }

    private Set<String> indexes(String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet rows = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
            Set<String> values = new HashSet<>();
            while (rows.next()) if (rows.getString("INDEX_NAME") != null) values.add(rows.getString("INDEX_NAME").toLowerCase());
            return values;
        }
    }
}
