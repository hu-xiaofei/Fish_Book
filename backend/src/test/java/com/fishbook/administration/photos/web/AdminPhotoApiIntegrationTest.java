package com.fishbook.administration.photos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fishbook.media.domain.*;
import com.fishbook.support.MySqlTestConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminPhotoApiIntegrationTest {
    static final String ADMIN = "admin-photo@example.com";
    static final String OWNER = "owner-photo@example.com";
    static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MediaStore store;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.fishbook.administration.photos.application.AdminPhotoQueryRepository queries;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.fishbook.administration.photos.application.AdminPhotoOperationRepository operations;
    long id;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM admin_photo_operations");
        jdbc.update("DELETE FROM media_cleanup_jobs");
        jdbc.update("DELETE FROM catch_records");
        jdbc.update("DELETE FROM users WHERE id IN (9701,9702,9703)");
        for (int i = 1; i <= 3; i++) {
            jdbc.update("INSERT INTO users (id,email,password_hash,nickname,role,status,created_at,updated_at) VALUES (?,?,'hash',?,?,'ACTIVE',NOW(),NOW())",
                    9700+i, i==1 ? ADMIN : i==2 ? OWNER : "other-photo@example.com", "nickname"+i, i==1 ? "ADMIN" : "USER");
        }
        id = insert(9702, "catches/9702/old");
    }
    long insert(long owner, String key) {
        jdbc.update("INSERT INTO catch_records (user_id,fish_species_id,caught_on,location,notes,photo_object_key,created_at,updated_at) VALUES (?,7,'2026-08-20','private lake','private notes',?,NOW(),NOW())",owner,key);
        return jdbc.queryForObject("SELECT MAX(id) FROM catch_records",Long.class);
    }
    MockMultipartFile photo() { return new MockMultipartFile("photo","test.jpg","image/jpeg",JPEG); }
    @Test void adminReadsForeignPhotoWithPrivateVersionHeaders() throws Exception {
        when(store.get("catches/9702/old")).thenReturn(new StoredMedia(JPEG,"image/jpeg"));
        mvc.perform(get("/api/v1/admin/photos/{id}/content",id).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().bytes(JPEG))
                .andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(header().string("ETag","\"0\""));
    }
    @Test void replaceAndRemoveAreAtomicVersionedAndDoNotChangePrivateDetails() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT,"/api/v1/admin/photos/{id}/content",id).file(photo())
                .header("If-Match","\"0\"").with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_photo_operations WHERE actor_user_id=9701 AND owner_user_id=9702 AND catch_record_id=? AND operation='REPLACED' AND previous_version=0",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs WHERE object_key='catches/9702/old' AND reason='REPLACED'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT notes FROM catch_records WHERE id=?",String.class,id)).isEqualTo("private notes");
        mvc.perform(delete("/api/v1/catches/{id}/photo",id).header("If-Match","\"0\"").with(user(OWNER)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATCH_PHOTO_CONFLICT"));
        mvc.perform(delete("/api/v1/admin/photos/{id}/content",id).header("If-Match","\"1\"").with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/photos/{id}/content",id).header("If-Match","\"2\"").with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_photo_operations",Integer.class)).isEqualTo(2);
        mvc.perform(get("/api/v1/admin/photos/{id}/operations?size=1",id).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].operation").value("REMOVED"))
                .andExpect(jsonPath("$.items[0].previousRevision").value("1"))
                .andExpect(jsonPath("$.items[0].actorUserId").value(9701))
                .andExpect(jsonPath("$.items[0].ownerUserId").value(9702))
                .andExpect(jsonPath("$.items[0].recordId").value(id))
                .andExpect(jsonPath("$.items[0].length()").value(7));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs",Integer.class)).isEqualTo(2);
        mvc.perform(get("/api/v1/admin/photos/{id}",id).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasPhoto").value(false)).andExpect(jsonPath("$.revision").value("2"));
        mvc.perform(get("/api/v1/admin/photos/{id}/content",id).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(multipart(HttpMethod.PUT,"/api/v1/admin/photos/{id}/content",id).file(photo()).header("If-Match","\"2\"").with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }
    @Test void ownerWinnerRejectsStaleAdminAndAdminCannotBypassOwnerEndpoint() throws Exception {
        mvc.perform(delete("/api/v1/catches/{id}/photo",id).header("If-Match","\"0\"").with(user(OWNER)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/photos/{id}/content",id).header("If-Match","\"0\"").with(user(ADMIN).roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/catches/{id}/photo",id).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_photo_operations",Integer.class)).isZero();
    }
    @Test void everyAdminRouteChecksWebAndCurrentDatabaseRoleBeforeStorage() throws Exception {
        for (String path : new String[]{"/api/v1/admin/photos","/api/v1/admin/photos/"+id,"/api/v1/admin/photos/"+id+"/operations","/api/v1/admin/photos/"+id+"/content"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user(OWNER))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user(OWNER).roles("ADMIN"))).andExpect(status().isForbidden());
        }
        for (String email : new String[]{OWNER,ADMIN}) {
            if (email.equals(ADMIN)) jdbc.update("UPDATE users SET status='DISABLED' WHERE id=9701");
            mvc.perform(multipart(HttpMethod.PUT,"/api/v1/admin/photos/{id}/content",id).file(photo()).header("If-Match","\"0\"").with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/admin/photos/{id}/content",id).header("If-Match","\"0\"").with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isForbidden());
        }
        mvc.perform(multipart(HttpMethod.PUT,"/api/v1/admin/photos/{id}/content",id).file(photo()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/admin/photos/{id}/content",id).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(HttpMethod.PUT,"/api/v1/admin/photos/{id}/content",id).file(photo()).with(user(OWNER)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/photos/{id}/content",id).with(user(OWNER)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(store);
        verifyNoInteractions(queries, operations);
    }
    @Test void strictVersionsAndCsrfAreRequiredForBothWriters() throws Exception {
        for (String path : new String[]{"/api/v1/admin/photos/"+id+"/content","/api/v1/catches/"+id+"/photo"}) {
            var actor = path.contains("admin") ? user(ADMIN).roles("ADMIN") : user(OWNER);
            mvc.perform(delete(path).with(actor).with(csrf())).andExpect(status().is(428)).andExpect(jsonPath("$.code").value("PHOTO_VERSION_REQUIRED"));
            mvc.perform(multipart(HttpMethod.PUT,path).file(photo()).with(actor).with(csrf())).andExpect(status().is(428));
            for (String revision : new String[]{"*","W/\"0\"","0","\"-1\"","\"0\",\"1\"","\"9223372036854775808\"",""}) {
                mvc.perform(delete(path).header("If-Match",revision).with(actor).with(csrf())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PHOTO_VERSION"));
                mvc.perform(multipart(HttpMethod.PUT,path).file(photo()).header("If-Match",revision).with(actor).with(csrf())).andExpect(status().isBadRequest());
            }
            mvc.perform(delete(path).header("If-Match","\"0\"").with(actor)).andExpect(status().isForbidden());
            mvc.perform(multipart(HttpMethod.PUT,path).file(photo()).header("If-Match","\"0\"").with(actor)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(store);
    }
    @Test void minimalFilteredPagesAndHistoryDoNotExposePrivateFields() throws Exception {
        insert(9703,"catches/9703/hidden"); insert(9702,null);
        String json=mvc.perform(get("/api/v1/admin/photos?userId=9702&page=0&size=1").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].recordId").value(id)).andExpect(jsonPath("$.items[0].revision").value("0"))
                .andExpect(jsonPath("$.items[0].length()").value(8))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("photoObjectKey","email","notes","location","status","password","catches/");
        mvc.perform(get("/api/v1/admin/photos/{id}/operations?page=0&size=1",id).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.pageable").doesNotExist());
        for (String query : new String[]{"userId=0","userId=bad","page=-1","page=2147483648","size=0","size=51","size=1.5"})
            mvc.perform(get("/api/v1/admin/photos?"+query).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ADMIN_PHOTO_QUERY"));
        for (String value : new String[]{"0","-1","oops","9223372036854775808"})
            mvc.perform(get("/api/v1/admin/photos/"+value).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ADMIN_PHOTO_QUERY"));
    }
    @Test void invalidMediaIsRejectedBeforeStorageAndUnavailableErrorsAreFixed() throws Exception {
        byte[] huge=new byte[10*1024*1024+1]; huge[0]=(byte)255; huge[1]=(byte)216; huge[2]=(byte)255;
        for (var bad : new MockMultipartFile[]{new MockMultipartFile("photo","bad","image/png",JPEG),new MockMultipartFile("photo","bad","image/jpeg",huge),new MockMultipartFile("photo","bad","image/jpeg",new byte[0])})
            mvc.perform(multipart(HttpMethod.PUT,"/api/v1/admin/photos/{id}/content",id).file(bad).header("If-Match","\"0\"").with(user(ADMIN).roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(store);
        when(store.get(anyString())).thenThrow(new MediaStorageUnavailableException(new RuntimeException("sentinel-secret key=catches/private")));
        String json=mvc.perform(get("/api/v1/admin/photos/{id}/content",id).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MEDIA_STORAGE_UNAVAILABLE")).andReturn().getResponse().getContentAsString();
        assertThat(json).contains("媒体存储暂时不可用").doesNotContain("sentinel-secret","catches/private");
    }
    @Test void ordinaryPhotoRoutesKeepForeignWritesIsolatedEvenForAdminSessions() throws Exception {
        for (String email : new String[]{ADMIN,"other-photo@example.com"}) {
            mvc.perform(multipart(HttpMethod.PUT,"/api/v1/catches/{id}/photo",id).file(photo())
                    .header("If-Match","\"0\"").with(user(email).roles("ADMIN")).with(csrf()))
                    .andExpect(status().isNotFound());
            mvc.perform(delete("/api/v1/catches/{id}/photo",id).header("If-Match","\"0\"")
                    .with(user(email).roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
        }
        verifyNoInteractions(store);
        assertThat(jdbc.queryForObject("SELECT photo_object_key FROM catch_records WHERE id=?",String.class,id)).isEqualTo("catches/9702/old");
    }
    @Test void pagingBreaksTimestampTiesByIdAndKeepsLargeRevisionsExact() throws Exception {
        long newer=insert(9703,"catches/9703/newer"); insert(9702,null);
        jdbc.update("UPDATE catch_records SET updated_at='2026-08-21 00:00:00',version=9007199254740993");
        mvc.perform(get("/api/v1/admin/photos?page=0&size=1").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].recordId").value(newer))
                .andExpect(jsonPath("$.items[0].revision").value("9007199254740993"))
                .andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/admin/photos?page=1&size=1").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].recordId").value(id));
        when(store.get("catches/9702/old")).thenReturn(new StoredMedia(JPEG,"image/jpeg"));
        mvc.perform(get("/api/v1/admin/photos/{id}/content",id).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("ETag","\"9007199254740993\""));
    }
}
