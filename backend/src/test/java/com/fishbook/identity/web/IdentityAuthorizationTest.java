package com.fishbook.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class IdentityAuthorizationTest {

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
    void currentUserWithoutSessionReturns401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION", Integer.class)).isZero();
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void csrfEndpointReturnsTokenAndHeaderName() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    void unlistedEndpointIsDenied() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
