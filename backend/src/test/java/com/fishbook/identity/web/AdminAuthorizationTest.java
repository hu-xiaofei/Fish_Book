package com.fishbook.identity.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishbook.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class AdminAuthorizationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousAdminReadRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/fishes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryUserCannotReadAdministration() throws Exception {
        mvc.perform(get("/api/v1/admin/fishes"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPassesTheRoleBoundary() throws Exception {
        mvc.perform(get("/api/v1/admin/fishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRootPassesTheRoleBoundary() throws Exception {
        mvc.perform(get("/api/v1/admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unrelatedMissingCatalogRouteRetainsGenericInternalErrorEnvelope() throws Exception {
        mvc.perform(get("/api/v1/fish/unmatched/path"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminWriteWithoutCsrfIsRejectedBeforeRouting() throws Exception {
        mvc.perform(post("/api/v1/admin/fishes")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}
