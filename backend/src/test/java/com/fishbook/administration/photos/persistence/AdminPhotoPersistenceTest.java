package com.fishbook.administration.photos.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fishbook.administration.photos.application.AdminPhotoOperation;
import com.fishbook.catchlog.domain.CatchRecord;
import com.fishbook.catchlog.domain.CatchRecordDetails;
import com.fishbook.catchlog.persistence.JpaCatchRecordRepositoryAdapter;
import com.fishbook.support.MySqlTestConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

@DataJpaTest
@Import({MySqlTestConfiguration.class, JpaCatchRecordRepositoryAdapter.class,
        JpaAdminPhotoOperationRepository.class})
class AdminPhotoPersistenceTest {
    private static final long USER_ID = 9_004L;
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JpaAdminPhotoOperationRepository operations;
    @Autowired private JpaCatchRecordRepositoryAdapter catches;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM admin_photo_operations");
        jdbc.update("DELETE FROM media_cleanup_jobs");
        jdbc.update("DELETE FROM catch_records");
        jdbc.update("DELETE FROM users WHERE id = ?", USER_ID);
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, nickname, role, status, created_at, updated_at)
                VALUES (?, 'audit-owner@example.com', 'hash', 'Owner', 'USER', 'ACTIVE', ?, ?)
                """, USER_ID, NOW, NOW);
    }

    @Test
    void returnsHistoryNewestFirstWithExactRevisionAndRetainsItAfterRecordDeletion() {
        // Bug caught: cascades could erase evidence or pagination could misorder equal timestamps.
        CatchRecord record = catches.save(record());
        operations.append(41L, USER_ID, record.id(), "REPLACED", Long.MAX_VALUE, NOW);
        operations.append(42L, USER_ID, record.id(), "REMOVED", 1L, NOW);
        operations.append(43L, USER_ID, record.id(), "REPLACED", 2L, NOW.plusSeconds(1));
        operations.append(44L, USER_ID, 999_999L, "REMOVED", 0L, NOW);

        var first = operations.findByRecordId(record.id(), 0, 2);
        assertThat(first.getTotalElements()).isEqualTo(3L);
        assertThat(first.getContent()).extracting(AdminPhotoOperation::actorUserId).containsExactly(43L, 42L);
        var second = operations.findByRecordId(record.id(), 1, 2);
        assertThat(second.getContent()).singleElement().satisfies(operation -> {
            assertThat(operation.previousRevision()).isEqualTo("9223372036854775807");
            assertThat(operation.operation()).isEqualTo("REPLACED");
            assertThat(operation.ownerUserId()).isEqualTo(USER_ID);
            assertThat(operation.recordId()).isEqualTo(record.id());
            assertThat(operation.occurredAt()).isEqualTo(NOW);
        });
        assertThat(catches.deleteByIdAndUserId(record.id(), USER_ID, record.version())).isTrue();
        assertThat(operations.findByRecordId(record.id(), 0, 20).getTotalElements()).isEqualTo(3L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackPhotoReferenceOperationAndCleanupTogether() {
        // Bug caught: a separate audit transaction could claim success after the business write rolls back.
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CatchRecord original = transaction.execute(status -> catches.save(record()));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            catches.save(original.withPhotoObjectKey("new", NOW.plusSeconds(1)));
            operations.append(41L, USER_ID, original.id(), "REPLACED", original.version(), NOW);
            jdbc.update("""
                    INSERT INTO media_cleanup_jobs
                    (object_key, reason, status, attempt_count, next_attempt_at, created_at)
                    VALUES ('old', 'REPLACED', 'PENDING', 0, ?, ?)
                    """, NOW, NOW);
            throw new IllegalStateException("force transaction rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT photo_object_key FROM catch_records WHERE id = ?",
                String.class, original.id())).isEqualTo("old");
        assertThat(jdbc.queryForObject("SELECT version FROM catch_records WHERE id = ?",
                Long.class, original.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_photo_operations", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs", Long.class)).isZero();
    }

    @Test
    void migratesVersionNineRowsWithoutChangingTheirPhotoOrDataAndEnforcesBoundedAudit() {
        // Bug caught: upgrading existing data could lose keys or accept unbounded audit payloads/types.
        try (MySQLContainer mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .target("9").load().migrate();
            JdbcTemplate migrationJdbc = new JdbcTemplate(new DriverManagerDataSource(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
            migrationJdbc.update("""
                    INSERT INTO users (id, email, password_hash, nickname, role, status, created_at, updated_at)
                    VALUES (41, 'legacy@example.com', 'hash', 'Legacy', 'USER', 'ACTIVE', ?, ?)
                    """, NOW, NOW);
            migrationJdbc.update("""
                    INSERT INTO catch_records (id, user_id, fish_species_id, caught_on, location,
                    notes, photo_object_key, created_at, updated_at)
                    VALUES (51, 41, 1, '2026-09-01', '原钓点', '原笔记', 'old/private/key', ?, ?)
                    """, NOW, NOW);
            Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .load().migrate();
            assertThat(migrationJdbc.queryForMap(
                    "SELECT version, location, notes, photo_object_key FROM catch_records WHERE id = 51"))
                    .containsEntry("version", 0L).containsEntry("location", "原钓点")
                    .containsEntry("notes", "原笔记").containsEntry("photo_object_key", "old/private/key");
            assertThat(migrationJdbc.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'admin_photo_operations'
                    ORDER BY ordinal_position
                    """, String.class)).containsExactly("id", "actor_user_id", "owner_user_id",
                    "catch_record_id", "operation", "previous_version", "occurred_at");
            migrationJdbc.update("""
                    INSERT INTO admin_photo_operations
                    (actor_user_id, owner_user_id, catch_record_id, operation, previous_version, occurred_at)
                    VALUES (42, 41, 51, 'REPLACED', 0, ?), (42, 41, 51, 'REMOVED', 1, ?)
                    """, NOW, NOW);
            assertThatThrownBy(() -> migrationJdbc.update("""
                    INSERT INTO admin_photo_operations
                    (actor_user_id, owner_user_id, catch_record_id, operation, previous_version, occurred_at)
                    VALUES (42, 41, 51, 'UNKNOWN', 0, ?)
                    """, NOW)).isInstanceOf(DataAccessException.class);
            migrationJdbc.update("DELETE FROM catch_records WHERE id = 51");
            assertThat(migrationJdbc.queryForObject(
                    "SELECT COUNT(*) FROM admin_photo_operations WHERE catch_record_id = 51", Long.class))
                    .isEqualTo(2L);
            migrationJdbc.update("""
                    INSERT INTO media_cleanup_jobs
                    (object_key, reason, status, attempt_count, next_attempt_at, created_at)
                    VALUES ('orphan', 'UPLOAD_ROLLBACK', 'PENDING', 0, ?, ?)
                    """, NOW, NOW);
            assertThat(migrationJdbc.queryForObject(
                    "SELECT reason FROM media_cleanup_jobs WHERE object_key = 'orphan'", String.class))
                    .isEqualTo("UPLOAD_ROLLBACK");
        }
    }

    private CatchRecord record() {
        return new CatchRecord(null, USER_ID, new CatchRecordDetails(
                1L, LocalDate.parse("2026-09-01"), "测试钓点", null, null, null, null), "old", NOW, NOW);
    }
}
