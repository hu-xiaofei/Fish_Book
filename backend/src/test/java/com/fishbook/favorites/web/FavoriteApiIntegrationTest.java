package com.fishbook.favorites.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishbook.support.MySqlTestConfiguration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class FavoriteApiIntegrationTest {

    private static final long USER_ID = 9101L;
    private static final long OTHER_USER_ID = 9102L;
    private static final String USER_EMAIL = "favorite-api@example.com";
    private static final String OTHER_USER_EMAIL = "other-favorite-api@example.com";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM favorites");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", USER_ID, OTHER_USER_ID);
        insertUser(USER_ID, USER_EMAIL);
        insertUser(OTHER_USER_ID, OTHER_USER_EMAIL);
    }

    @Test
    void repeatedPutAndDeleteRemainIdempotent() throws Exception {
        mvc.perform(put("/api/v1/favorites/channa-argus")
                        .with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/favorites/channa-argus")
                        .with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE user_id = ?",
                Integer.class,
                USER_ID)).isEqualTo(1);

        mvc.perform(delete("/api/v1/favorites/channa-argus")
                        .with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/favorites/channa-argus")
                        .with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE user_id = ?",
                Integer.class,
                USER_ID)).isZero();
    }

    @Test
    void listDefaultsToPageZeroAndReturnsOnlyCurrentUsersFavorites() throws Exception {
        addFavorite(USER_EMAIL, "channa-argus");
        addFavorite(OTHER_USER_EMAIL, "cyprinus-carpio");

        mvc.perform(get("/api/v1/favorites").with(user(USER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("channa-argus"))
                .andExpect(jsonPath("$.items[0].commonNameZh").value("乌鳢"))
                .andExpect(jsonPath("$.items[0].favoritedAt").isNotEmpty());
    }

    @Test
    void statusAcceptsRepeatedSlugsAndPreservesFirstSeenUniqueOrder() throws Exception {
        addFavorite(USER_EMAIL, "channa-argus");

        mvc.perform(get("/api/v1/favorites/status")
                        .with(user(USER_EMAIL))
                        .param("fishSlug", "cyprinus-carpio", "channa-argus", "cyprinus-carpio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].fishSlug").value("cyprinus-carpio"))
                .andExpect(jsonPath("$.items[0].favorited").value(false))
                .andExpect(jsonPath("$.items[1].fishSlug").value("channa-argus"))
                .andExpect(jsonPath("$.items[1].favorited").value(true));
    }

    @Test
    void missingStatusSlugsReturnStableFavoriteQueryError() throws Exception {
        mvc.perform(get("/api/v1/favorites/status").with(user(USER_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FAVORITE_QUERY"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void invalidFavoriteQueriesReturnStableError() throws Exception {
        mvc.perform(get("/api/v1/favorites").with(user(USER_EMAIL)).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FAVORITE_QUERY"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/favorites").with(user(USER_EMAIL)).param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FAVORITE_QUERY"));
        mvc.perform(get("/api/v1/favorites").with(user(USER_EMAIL)).param("size", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FAVORITE_QUERY"));

        String[] thirteenSlugs = IntStream.rangeClosed(1, 13)
                .mapToObj(index -> "fish-" + index)
                .toArray(String[]::new);
        mvc.perform(get("/api/v1/favorites/status")
                        .with(user(USER_EMAIL))
                        .param("fishSlug", thirteenSlugs))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FAVORITE_QUERY"));
    }

    @Test
    void missingFishReturnsStableNotFoundError() throws Exception {
        mvc.perform(put("/api/v1/favorites/missing-fish")
                        .with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FISH_NOT_FOUND"));
    }

    private void addFavorite(String email, String fishSlug) throws Exception {
        mvc.perform(put("/api/v1/favorites/{fishSlug}", fishSlug)
                        .with(user(email)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private void insertUser(long id, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, nickname, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', 'Favorites', 'USER', 'ACTIVE',
                    '2026-08-14 00:00:00.000000', '2026-08-14 00:00:00.000000')
                """, id, email);
    }
}
