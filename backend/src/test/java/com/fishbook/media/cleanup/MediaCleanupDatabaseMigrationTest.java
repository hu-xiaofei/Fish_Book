package com.fishbook.media.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fishbook.support.MySqlTestConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(MySqlTestConfiguration.class)
class MediaCleanupDatabaseMigrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsBoundedCleanupQueueWithDueIndexAndNullableAttemptTimes() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class)).contains("media_cleanup_jobs");

        assertThat(jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name = 'media_cleanup_jobs'",
                String.class)).contains(
                        "PRIMARY",
                        "ck_media_cleanup_reason",
                        "ck_media_cleanup_status",
                        "ck_media_cleanup_attempt_count");

        assertThat(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'media_cleanup_jobs' "
                        + "AND index_name = 'ix_media_cleanup_due' ORDER BY seq_in_index",
                String.class)).containsExactly("status", "next_attempt_at", "id");

        List<Map<String, Object>> nullableColumns = jdbcTemplate.queryForList(
                "SELECT column_name, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'media_cleanup_jobs' "
                        + "AND column_name IN ('next_attempt_at', 'last_attempt_at')");
        assertThat(nullableColumns)
                .extracting(row -> row.get("COLUMN_NAME"), row -> row.get("IS_NULLABLE"))
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("next_attempt_at", "YES"),
                        org.assertj.core.groups.Tuple.tuple("last_attempt_at", "YES"));

        jdbcTemplate.update("""
                INSERT INTO media_cleanup_jobs (
                    object_key, reason, status, attempt_count,
                    next_attempt_at, last_attempt_at, created_at
                ) VALUES ('catches/1/2/object', 'REPLACED', 'PENDING', 0,
                    '2026-08-24 01:00:00.000000', NULL, '2026-08-24 01:00:00.000000')
                """);

        assertConstraintRejects("UNKNOWN", "PENDING", 0);
        assertConstraintRejects("REMOVED", "DONE", 0);
        assertConstraintRejects("RECORD_DELETED", "PENDING", -1);
        assertConstraintRejects("RECORD_DELETED", "PENDING", 9);
    }

    private void assertConstraintRejects(String reason, String status, int attemptCount) {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO media_cleanup_jobs (
                    object_key, reason, status, attempt_count,
                    next_attempt_at, last_attempt_at, created_at
                ) VALUES (?, ?, ?, ?, NULL, NULL, '2026-08-24 01:00:00.000000')
                """, "catches/invalid/" + reason + status + attemptCount, reason, status, attemptCount))
                .isInstanceOf(DataAccessException.class);
    }
}
