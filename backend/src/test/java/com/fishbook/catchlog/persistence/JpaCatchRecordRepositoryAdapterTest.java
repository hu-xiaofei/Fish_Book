package com.fishbook.catchlog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishbook.catchlog.domain.CatchRecord;
import com.fishbook.catchlog.domain.CatchRecordDetails;
import com.fishbook.catchlog.domain.CatchRecordPage;
import com.fishbook.support.MySqlTestConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({MySqlTestConfiguration.class, JpaCatchRecordRepositoryAdapter.class})
class JpaCatchRecordRepositoryAdapterTest {
    private static final long USER_ID = 9_002L;
    private static final long OTHER_USER_ID = 9_003L;

    @Autowired
    private JpaCatchRecordRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM catch_records");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", USER_ID, OTHER_USER_ID);
        insertUser(USER_ID, "catch-repository@example.com");
        insertUser(OTHER_USER_ID, "other-catch-repository@example.com");
    }

    @Test
    void savesReadsUpdatesAndDeletesTheOwnersRecord() {
        // Bug caught: saves could lose nullable fields or updates could replace immutable audit data.
        CatchRecord saved = adapter.save(recordFor(
                USER_ID, 1L, LocalDate.parse("2026-08-20"),
                Instant.parse("2026-08-20T01:00:00Z"), "我的水库", null));

        assertThat(saved.id()).isPositive();
        assertThat(saved.photoObjectKey()).isNull();
        assertThat(adapter.findByIdAndUserId(saved.id(), USER_ID)).contains(saved);

        CatchRecord updated = adapter.save(saved.update(
                new CatchRecordDetails(
                        2L, LocalDate.parse("2026-08-19"), "更新后的钓点",
                        new BigDecimal("42.50"), new BigDecimal("1350.00"),
                        "路亚", "傍晚中鱼"),
                Instant.parse("2026-08-20T02:00:00Z")));

        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(updated.createdAt()).isEqualTo(saved.createdAt());
        assertThat(adapter.findByIdAndUserId(saved.id(), USER_ID))
                .hasValueSatisfying(record -> {
                    assertThat(record.details().fishId()).isEqualTo(2L);
                    assertThat(record.details().location()).isEqualTo("更新后的钓点");
                    assertThat(record.updatedAt()).isEqualTo(Instant.parse("2026-08-20T02:00:00Z"));
                });
        assertThat(adapter.deleteByIdAndUserId(saved.id(), USER_ID)).isTrue();
        assertThat(adapter.findByIdAndUserId(saved.id(), USER_ID)).isEmpty();
        assertThat(adapter.deleteByIdAndUserId(saved.id(), USER_ID)).isFalse();
    }

    @Test
    void scopesDetailUpdateAndDeleteByBothRecordAndUser() {
        // Bug caught: a guessed record ID could reveal or delete another user's private catch.
        CatchRecord saved = adapter.save(recordFor(
                USER_ID, 1L, LocalDate.parse("2026-08-20"),
                Instant.parse("2026-08-20T01:00:00Z"), "我的水库", null));

        assertThat(adapter.findByIdAndUserId(saved.id(), OTHER_USER_ID)).isEmpty();
        assertThat(adapter.deleteByIdAndUserId(saved.id(), OTHER_USER_ID)).isFalse();
        assertThat(adapter.findByIdAndUserId(saved.id(), USER_ID)).contains(saved);
    }

    @Test
    void pagesByCaughtOnCreatedAtAndIdDescendingWithoutOtherUsers() {
        // Bug caught: paging could leak another user's records or shuffle same-timestamp items.
        CatchRecord lowerId = adapter.save(recordFor(
                USER_ID, 1L, LocalDate.parse("2026-08-20"),
                Instant.parse("2026-08-20T01:00:00Z"), "一号点", null));
        CatchRecord higherId = adapter.save(recordFor(
                USER_ID, 2L, LocalDate.parse("2026-08-20"),
                Instant.parse("2026-08-20T01:00:00Z"), "二号点", null));
        adapter.save(recordFor(
                USER_ID, 3L, LocalDate.parse("2026-08-19"),
                Instant.parse("2026-08-20T02:00:00Z"), "三号点", null));
        adapter.save(recordFor(
                OTHER_USER_ID, 4L, LocalDate.parse("2026-08-21"),
                Instant.parse("2026-08-20T03:00:00Z"), "其他用户", null));

        CatchRecordPage first = adapter.findByUserId(USER_ID, 0, 2);
        CatchRecordPage second = adapter.findByUserId(USER_ID, 1, 2);

        assertThat(first.items()).extracting(CatchRecord::id)
                .containsExactly(higherId.id(), lowerId.id());
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.items()).extracting(CatchRecord::details)
                .extracting(CatchRecordDetails::location).containsExactly("三号点");
    }

    @Test
    void restoresPersistedFutureDatesWithoutApplyingCurrentDateValidation() {
        // Bug caught: hydration could treat a valid historical row as a new submission and reject it by date.
        jdbcTemplate.update("""
                INSERT INTO catch_records (
                    id, user_id, fish_species_id, caught_on, location, length_cm, weight_g,
                    method, notes, photo_object_key, created_at, updated_at
                ) VALUES (7001, ?, 1, '2099-01-01', '历史钓点', NULL, NULL,
                    NULL, NULL, 'catch/7001/photo.jpg',
                    '2026-08-20 01:00:00.000000', '2026-08-20 02:00:00.000000')
                """, USER_ID);

        assertThat(adapter.findByIdAndUserId(7001L, USER_ID))
                .hasValueSatisfying(record -> {
                    assertThat(record.details().caughtOn()).isEqualTo(LocalDate.parse("2099-01-01"));
                    assertThat(record.photoObjectKey()).isEqualTo("catch/7001/photo.jpg");
                });
    }

    private CatchRecord recordFor(
            long userId, long fishId, LocalDate caughtOn, Instant createdAt,
            String location, String photoObjectKey) {
        return new CatchRecord(
                null,
                userId,
                new CatchRecordDetails(
                        fishId, caughtOn, location,
                        new BigDecimal("42.50"), new BigDecimal("1350.00"),
                        "手竿", "清晨近岸中鱼"),
                photoObjectKey,
                createdAt,
                createdAt);
    }

    private void insertUser(long id, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, nickname, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', 'Catch', 'USER', 'ACTIVE',
                    '2026-08-20 00:00:00.000000', '2026-08-20 00:00:00.000000')
                """, id, email);
    }
}
