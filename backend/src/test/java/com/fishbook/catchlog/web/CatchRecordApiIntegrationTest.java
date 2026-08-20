package com.fishbook.catchlog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishbook.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
// SecurityMockMvc csrf()/user() mutate the shared filter chain; do not leak it to later tests.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatchRecordApiIntegrationTest {

    private static final long USER_ID = 9401L;
    private static final long OTHER_USER_ID = 9402L;
    private static final String USER_EMAIL = "catch-api@example.com";
    private static final String OTHER_USER_EMAIL = "other-catch-api@example.com";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM catch_records");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", USER_ID, OTHER_USER_ID);
        insertUser(USER_ID, USER_EMAIL);
        insertUser(OTHER_USER_ID, OTHER_USER_EMAIL);
    }

    private void insertUser(long id, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, nickname, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', 'Catch API', 'USER', 'ACTIVE',
                    '2026-08-14 00:00:00.000000', '2026-08-14 00:00:00.000000')
                """, id, email);
    }

    @Test
    void createReturnsLocationAndCompleteRecordWithoutPhotoObjectKey() throws Exception {
        mvc.perform(post("/api/v1/catches")
                        .with(user(USER_EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fishSlug":"channa-argus","caughtOn":"2026-08-20",
                                 "location":"城郊水库","lengthCm":42.5,"weightG":1350,
                                 "method":"路亚","notes":"傍晚近岸中鱼"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/catches/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fishSlug").value("channa-argus"))
                .andExpect(jsonPath("$.commonNameZh").value("乌鳢"))
                .andExpect(jsonPath("$.caughtOn").value("2026-08-20"))
                .andExpect(jsonPath("$.location").value("城郊水库"))
                .andExpect(jsonPath("$.lengthCm").value(42.5))
                .andExpect(jsonPath("$.weightG").value(1350))
                .andExpect(jsonPath("$.method").value("路亚"))
                .andExpect(jsonPath("$.notes").value("傍晚近岸中鱼"))
                .andExpect(jsonPath("$.hasPhoto").value(false))
                .andExpect(jsonPath("$.photoObjectKey").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void listUsesFixedPagingForMultipleRecordsOfTheSameFish() throws Exception {
        long ownNewer = insertCatchFor(USER_ID, "2026-08-19", "Newest", "private-key");
        insertCatchFor(USER_ID, "2026-08-18", "Older", null);
        insertCatchFor(OTHER_USER_ID, "2026-08-20", "Other user's record", null);

        mvc.perform(get("/api/v1/catches").with(user(USER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(ownNewer))
                .andExpect(jsonPath("$.items[0].fishSlug").value("channa-argus"))
                .andExpect(jsonPath("$.items[0].location").value("Newest"))
                .andExpect(jsonPath("$.items[0].hasPhoto").value(true))
                .andExpect(jsonPath("$.items[1].fishSlug").value("channa-argus"))
                .andExpect(jsonPath("$.items[0].notes").doesNotExist())
                .andExpect(jsonPath("$.items[0].photoObjectKey").doesNotExist());
    }

    @Test
    void getAndUpdateReturnCompleteOwnedRecord() throws Exception {
        long id = insertCatchFor(USER_ID, "2026-08-18", "Old location", null);

        mvc.perform(get("/api/v1/catches/{id}", id).with(user(USER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.notes").value("seed notes"))
                .andExpect(jsonPath("$.photoObjectKey").doesNotExist());

        mvc.perform(put("/api/v1/catches/{id}", id)
                        .with(user(USER_EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fishSlug":"cyprinus-carpio","caughtOn":"2026-08-19",
                                 "location":"New location","lengthCm":0,"weightG":0,
                                 "method":"","notes":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.fishSlug").value("cyprinus-carpio"))
                .andExpect(jsonPath("$.caughtOn").value("2026-08-19"))
                .andExpect(jsonPath("$.location").value("New location"))
                .andExpect(jsonPath("$.lengthCm").value(0))
                .andExpect(jsonPath("$.weightG").value(0))
                .andExpect(jsonPath("$.method").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.hasPhoto").value(false));
    }

    @Test
    void deleteReturnsNoContentThenNotFound() throws Exception {
        long id = insertCatchFor(USER_ID, "2026-08-18", "Delete me", null);

        mvc.perform(delete("/api/v1/catches/{id}", id).with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM catch_records WHERE id = ?", Integer.class, id)).isZero();

        mvc.perform(delete("/api/v1/catches/{id}", id).with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATCH_RECORD_NOT_FOUND"));
    }

    @Test
    void anotherUsersRecordUsesTheSameNotFoundContractForReadUpdateAndDelete() throws Exception {
        long id = insertCatchFor(USER_ID, "2026-08-18", "Private", null);

        mvc.perform(get("/api/v1/catches/{id}", id).with(user(OTHER_USER_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATCH_RECORD_NOT_FOUND"));
        mvc.perform(put("/api/v1/catches/{id}", id)
                        .with(user(OTHER_USER_EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATCH_RECORD_NOT_FOUND"));
        mvc.perform(delete("/api/v1/catches/{id}", id)
                        .with(user(OTHER_USER_EMAIL)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATCH_RECORD_NOT_FOUND"));
    }

    @Test
    void invalidCatchInputAndQueryUseStableCatchError() throws Exception {
        mvc.perform(post("/api/v1/catches")
                        .with(user(USER_EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fishSlug":"channa-argus","caughtOn":"not-a-date","location":"Lake"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATCH_RECORD"))
                .andExpect(jsonPath("$.message").value("Catch record is invalid"));
        mvc.perform(get("/api/v1/catches").with(user(USER_EMAIL)).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATCH_RECORD"));
        mvc.perform(get("/api/v1/catches").with(user(USER_EMAIL)).param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATCH_RECORD"));
        mvc.perform(get("/api/v1/catches").with(user(USER_EMAIL)).param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATCH_RECORD"));
    }

    @Test
    void dateBelowMySqlLowerBoundReturnsStableBadRequestWithoutSaving() throws Exception {
        int before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM catch_records WHERE user_id = ?", Integer.class, USER_ID);

        mvc.perform(post("/api/v1/catches")
                        .with(user(USER_EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fishSlug":"channa-argus","caughtOn":"0999-12-31","location":"Lake"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATCH_RECORD"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM catch_records WHERE user_id = ?", Integer.class, USER_ID))
                .isEqualTo(before);
    }

    @Test
    void malformedAndOverflowCatchIdsReturnStableBadRequestForEveryOwnedRecordMethod() throws Exception {
        for (String invalidId : new String[]{"not-a-number", "999999999999999999999999999999999999"}) {
            mvc.perform(get("/api/v1/catches/{id}", invalidId).with(user(USER_EMAIL)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CATCH_RECORD"));
            mvc.perform(put("/api/v1/catches/{id}", invalidId)
                            .with(user(USER_EMAIL)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequest()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CATCH_RECORD"));
            mvc.perform(delete("/api/v1/catches/{id}", invalidId)
                            .with(user(USER_EMAIL)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CATCH_RECORD"));
        }
    }

    @Test
    void malformedJsonRemainsInvalidRequestAndMissingFishUsesCatalogNotFound() throws Exception {
        mvc.perform(post("/api/v1/catches")
                        .with(user(USER_EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fishSlug\":\"channa-argus\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/catches")
                        .with(user(USER_EMAIL)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fishSlug":"missing-fish","caughtOn":"2026-08-20","location":"Lake"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FISH_NOT_FOUND"));
    }

    private long insertCatchFor(long userId, String caughtOn, String location, String photoObjectKey) {
        return insertCatchFor(userId, 7L, caughtOn, location, photoObjectKey);
    }

    private long insertCatchFor(
            long userId, long fishId, String caughtOn, String location, String photoObjectKey) {
        jdbcTemplate.update("""
                INSERT INTO catch_records (
                    user_id, fish_species_id, caught_on, location, length_cm, weight_g, method, notes,
                    photo_object_key, created_at, updated_at)
                VALUES (?, ?, ?, ?, 42.50, 1350.00, 'lure', 'seed notes', ?,
                    '2026-08-14 00:00:00.000000', '2026-08-14 00:00:00.000000')
                """, userId, fishId, caughtOn, location, photoObjectKey);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private static String validRequest() {
        return """
                {"fishSlug":"channa-argus","caughtOn":"2026-08-20","location":"Lake"}
                """;
    }
}
