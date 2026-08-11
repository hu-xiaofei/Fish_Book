package com.fishbook.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
class CatalogAuthorizationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSessionData() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
    }

    @Test
    void anonymousCatalogReadsArePublic() throws Exception {
        mvc.perform(get("/api/v1/fish"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
        mvc.perform(get("/api/v1/fish/filters")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/fish/channa-argus")).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION", Integer.class)).isZero();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void catalogWritesRemainDeniedWithValidCsrf() throws Exception {
        assertDenied(post("/api/v1/fish"));
        assertDenied(put("/api/v1/fish/channa-argus"));
        assertDenied(patch("/api/v1/fish/channa-argus"));
        assertDenied(delete("/api/v1/fish/channa-argus"));
    }

    private void assertDenied(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mvc.perform(request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
