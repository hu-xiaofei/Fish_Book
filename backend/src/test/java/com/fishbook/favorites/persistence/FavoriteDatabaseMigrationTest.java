package com.fishbook.favorites.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fishbook.support.MySqlTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(MySqlTestConfiguration.class)
class FavoriteDatabaseMigrationTest {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void createsFavoritesTableWithRequiredKeysAndUniqueUserFishPair() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
        assertThat(tables).contains("favorites");

        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name = 'favorites'",
                String.class);
        assertThat(constraints).contains(
                "uk_favorites_user_fish", "fk_favorites_user", "fk_favorites_fish");

        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'favorites'",
                String.class);
        assertThat(indexes).contains("ix_favorites_user_created_id");

        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, nickname, role, status, created_at, updated_at)
                VALUES (9001, 'favorite-migration@example.com', 'hash', 'Migration', 'USER', 'ACTIVE',
                    '2026-08-14 00:00:00.000000', '2026-08-14 00:00:00.000000')
                """);
        jdbcTemplate.update("""
                INSERT INTO favorites (user_id, fish_species_id, created_at)
                VALUES (9001, 1, '2026-08-14 00:00:00.000000')
                """);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO favorites (user_id, fish_species_id, created_at)
                VALUES (9001, 1, '2026-08-14 00:00:01.000000')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
