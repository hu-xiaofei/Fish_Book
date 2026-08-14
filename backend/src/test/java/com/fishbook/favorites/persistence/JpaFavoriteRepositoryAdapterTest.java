package com.fishbook.favorites.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishbook.favorites.domain.FavoriteEntry;
import com.fishbook.favorites.domain.FavoritePage;
import com.fishbook.support.MySqlTestConfiguration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({MySqlTestConfiguration.class, JpaFavoriteRepositoryAdapter.class})
class JpaFavoriteRepositoryAdapterTest {
    private static final long USER_ID = 9002L;
    private static final long OTHER_USER_ID = 9003L;

    @Autowired
    private JpaFavoriteRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM favorites");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", USER_ID, OTHER_USER_ID);
        insertUser(USER_ID, "favorite-repository@example.com");
        insertUser(OTHER_USER_ID, "other-favorite-repository@example.com");
    }

    @Test
    void addingTheSameFishTwiceCreatesOneFavorite() {
        Instant favoritedAt = Instant.parse("2026-08-14T00:00:00Z");

        adapter.add(USER_ID, 1L, favoritedAt);
        adapter.add(USER_ID, 1L, favoritedAt.plusSeconds(1));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE user_id = ? AND fish_species_id = ?",
                Integer.class, USER_ID, 1L)).isEqualTo(1);
        assertThat(adapter.findByUserId(USER_ID, 0, 10).items())
                .extracting(FavoriteEntry::favoritedAt)
                .containsExactly(favoritedAt);
    }

    @Test
    void removingTheSameFishTwiceLeavesNoFavorite() {
        adapter.add(USER_ID, 1L, Instant.parse("2026-08-14T00:00:00Z"));

        adapter.remove(USER_ID, 1L);
        adapter.remove(USER_ID, 1L);

        assertThat(adapter.findByUserId(USER_ID, 0, 10).items()).isEmpty();
    }

    @Test
    void pagesFavoritesByCreatedAtThenIdDescending() {
        adapter.add(USER_ID, 1L, Instant.parse("2026-08-14T00:00:00Z"));
        adapter.add(USER_ID, 2L, Instant.parse("2026-08-14T00:00:02Z"));
        adapter.add(USER_ID, 3L, Instant.parse("2026-08-14T00:00:02Z"));

        FavoritePage first = adapter.findByUserId(USER_ID, 0, 2);
        FavoritePage second = adapter.findByUserId(USER_ID, 1, 2);

        assertThat(first.items()).extracting(FavoriteEntry::fishId).containsExactly(3L, 2L);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.items()).extracting(FavoriteEntry::fishId).containsExactly(1L);
    }

    @Test
    void findsOnlyTheCurrentUsersFavoritedFishIds() {
        adapter.add(USER_ID, 1L, Instant.parse("2026-08-14T00:00:00Z"));
        adapter.add(USER_ID, 3L, Instant.parse("2026-08-14T00:00:01Z"));
        adapter.add(OTHER_USER_ID, 2L, Instant.parse("2026-08-14T00:00:02Z"));

        Set<Long> favoritedFishIds = adapter.findFavoritedFishIds(USER_ID, Set.of(1L, 2L, 3L, 4L));

        assertThat(favoritedFishIds).containsExactlyInAnyOrder(1L, 3L);
    }

    private void insertUser(long id, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, nickname, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', 'Favorites', 'USER', 'ACTIVE',
                    '2026-08-14 00:00:00.000000', '2026-08-14 00:00:00.000000')
                """, id, email);
    }
}
