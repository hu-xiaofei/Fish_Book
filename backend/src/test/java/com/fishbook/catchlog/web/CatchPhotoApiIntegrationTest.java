package com.fishbook.catchlog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishbook.media.domain.MediaStorageUnavailableException;
import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.domain.StoredMedia;
import com.fishbook.support.MySqlTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
// SecurityMockMvc csrf()/user() mutate the shared filter chain; do not leak it to later tests.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatchPhotoApiIntegrationTest {
    private static final long USER_ID = 9601L;
    private static final long OTHER_USER_ID = 9602L;
    private static final String USER_EMAIL = "photo-api@example.com";
    private static final String OTHER_USER_EMAIL = "other-photo-api@example.com";
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01
    };
    private static final byte[] WEBP = {
        0x52, 0x49, 0x46, 0x46, 0x01, 0x02, 0x03, 0x04,
        0x57, 0x45, 0x42, 0x50, 0x01
    };

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    MediaStore mediaStore;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM media_cleanup_jobs");
        jdbcTemplate.update("DELETE FROM catch_records");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", USER_ID, OTHER_USER_ID);
        insertUser(USER_ID, USER_EMAIL);
        insertUser(OTHER_USER_ID, OTHER_USER_EMAIL);
    }

    @Test
    void acceptsJpegPngAndWebpAndStoresOnlyPrivateOpaqueKeys() throws Exception {
        for (var fixture : List.of(
                new PhotoFixture("photo.jpg", "image/jpeg", JPEG),
                new PhotoFixture("photo.png", "image/png", PNG),
                new PhotoFixture("photo.webp", "image/webp", WEBP))) {
            long id = insertCatch(USER_ID, null);

            mvc.perform(multipart(HttpMethod.PUT, "/api/v1/catches/{id}/photo", id)
                            .file(new MockMultipartFile(
                                    "photo", fixture.filename(), fixture.contentType(), fixture.content()))
                            .with(user(USER_EMAIL)).with(csrf()))
                    .andExpect(status().isNoContent());

            String objectKey = jdbcTemplate.queryForObject(
                    "SELECT photo_object_key FROM catch_records WHERE id = ?", String.class, id);
            assertThat(objectKey).matches("catches/" + USER_ID + "/" + id + "/[0-9a-f-]{36}");
            verify(mediaStore).put(eq(objectKey), eq(fixture.content()), eq(fixture.contentType()));
        }
    }

    @Test
    void ownerGetsBinaryWithPrivateSafeHeadersAndNoObjectKeyDisclosure() throws Exception {
        long id = insertCatch(USER_ID, "catches/9601/private-object-key");
        when(mediaStore.get("catches/9601/private-object-key"))
                .thenReturn(new StoredMedia(JPEG, "image/jpeg"));

        mvc.perform(get("/api/v1/catches/{id}/photo", id).with(user(USER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG))
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private"))
                .andExpect(header().doesNotExist("X-Object-Key"));
    }

    @Test
    void rejectsMissingEmptyMismatchedGifAndOversizedPhotosWithoutStorageCalls() throws Exception {
        long id = insertCatch(USER_ID, null);

        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/catches/{id}/photo", id)
                        .with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATCH_PHOTO"));
        assertInvalid(id, new MockMultipartFile("photo", "empty.jpg", "image/jpeg", new byte[0]));
        assertInvalid(id, new MockMultipartFile("photo", "wrong.png", "image/png", JPEG));
        assertInvalid(id, new MockMultipartFile(
                "photo", "photo.gif", "image/gif", new byte[] {'G', 'I', 'F', '8', '9', 'a'}));
        var oversized = new byte[10 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0xff;
        oversized[1] = (byte) 0xd8;
        oversized[2] = (byte) 0xff;
        assertInvalid(id, new MockMultipartFile("photo", "huge.jpg", "image/jpeg", oversized));

        verifyNoInteractions(mediaStore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT photo_object_key FROM catch_records WHERE id = ?", String.class, id)).isNull();
    }

    @Test
    void noPhotoAndForeignPhotoUseTheSameNotFoundResponseWithoutReadingStorage() throws Exception {
        long noPhoto = insertCatch(USER_ID, null);
        long foreignPhoto = insertCatch(USER_ID, "catches/9601/foreign/private");

        mvc.perform(get("/api/v1/catches/{id}/photo", noPhoto).with(user(USER_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATCH_PHOTO_NOT_FOUND"));
        mvc.perform(get("/api/v1/catches/{id}/photo", foreignPhoto).with(user(OTHER_USER_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATCH_PHOTO_NOT_FOUND"));

        verify(mediaStore, never()).get(anyString());
    }

    @Test
    void ownerDeleteIsIdempotentAndQueuesAnExistingObjectForCleanup() throws Exception {
        long id = insertCatch(USER_ID, "catches/9601/remove/private");

        mvc.perform(delete("/api/v1/catches/{id}/photo", id)
                        .with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/catches/{id}/photo", id)
                        .with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT photo_object_key FROM catch_records WHERE id = ?", String.class, id)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_cleanup_jobs WHERE object_key = ? AND reason = 'REMOVED'",
                Integer.class,
                "catches/9601/remove/private")).isEqualTo(1);
    }

    @Test
    void putAndDeleteRequireCsrf() throws Exception {
        long id = insertCatch(USER_ID, null);

        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/catches/{id}/photo", id)
                        .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", JPEG))
                        .with(user(USER_EMAIL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(delete("/api/v1/catches/{id}/photo", id).with(user(USER_EMAIL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void unavailableStorageUsesStableServiceUnavailableResponse() throws Exception {
        long uploadId = insertCatch(USER_ID, null);
        doThrow(new MediaStorageUnavailableException())
                .when(mediaStore).put(anyString(), any(byte[].class), eq("image/jpeg"));

        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/catches/{id}/photo", uploadId)
                        .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", JPEG))
                        .with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEDIA_STORAGE_UNAVAILABLE"));

        long readId = insertCatch(USER_ID, "catches/9601/unavailable/private");
        when(mediaStore.get("catches/9601/unavailable/private"))
                .thenThrow(new MediaStorageUnavailableException());
        mvc.perform(get("/api/v1/catches/{id}/photo", readId).with(user(USER_EMAIL)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEDIA_STORAGE_UNAVAILABLE"));
    }

    @Test
    void storageFailureDoesNotExposeSdkDetails() throws Exception {
        long id = insertCatch(USER_ID, "catches/9601/private");
        var failure = new MediaStorageUnavailableException(new IllegalStateException(
                "sentinel-secret endpoint=https://private.invalid key=catches/private"));
        when(mediaStore.get("catches/9601/private")).thenThrow(failure);

        var result = mvc.perform(get("/api/v1/catches/{id}/photo", id).with(user(USER_EMAIL)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEDIA_STORAGE_UNAVAILABLE"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("媒体存储暂时不可用")
                .doesNotContain("sentinel-secret", "private.invalid", "catches/private");
    }

    private void assertInvalid(long id, MockMultipartFile photo) throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/catches/{id}/photo", id)
                        .file(photo).with(user(USER_EMAIL)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATCH_PHOTO"));
    }

    private void insertUser(long id, String email) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, nickname, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', 'Photo API', 'USER', 'ACTIVE',
                    '2026-08-14 00:00:00.000000', '2026-08-14 00:00:00.000000')
                """, id, email);
    }

    private long insertCatch(long userId, String photoObjectKey) {
        jdbcTemplate.update("""
                INSERT INTO catch_records (
                    user_id, fish_species_id, caught_on, location, length_cm, weight_g, method, notes,
                    photo_object_key, created_at, updated_at)
                VALUES (?, 7, '2026-08-20', 'Lake', 42.50, 1350.00, 'lure', 'private notes', ?,
                    '2026-08-14 00:00:00.000000', '2026-08-14 00:00:00.000000')
                """, userId, photoObjectKey);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private record PhotoFixture(String filename, String contentType, byte[] content) {}
}
