package com.fishbook.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishbook.identity.web.dto.RegisterRequest;
import com.fishbook.support.MySqlTestConfiguration;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class AuthFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SessionRepository<? extends Session> sessionRepository;

    @Autowired
    CookieSerializer cookieSerializer;

    @BeforeEach
    void clearIdentityAndSessionData() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void registersUserAndRejectsDuplicateNormalizedEmail() throws Exception {
        String body = """
                {"email":"Angler@Example.COM","password":"strong-pass","nickname":"Wall_E"}
                """;
        Cookie csrfCookie = fetchCsrfCookie();

        mvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("angler@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void acceptsRawCsrfCookieValueReplayedBySpaHeader() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();

        mvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"angler@example.com","password":"strong-pass","nickname":"Wall_E"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void successfulLoginExpiresPreLoginCsrfTokenAndIssuesFreshTokenOnBootstrap()
            throws Exception {
        Cookie preLoginCsrf = fetchCsrfCookie();

        mvc.perform(post("/api/v1/auth/register")
                        .cookie(preLoginCsrf)
                        .header("X-XSRF-TOKEN", preLoginCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"angler@example.com","password":"strong-pass","nickname":"Wall_E"}
                                """))
                .andExpect(status().isCreated());

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .cookie(preLoginCsrf)
                        .header("X-XSRF-TOKEN", preLoginCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"angler@example.com\",\"password\":\"strong-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0))
                .andExpect(cookie().exists("JSESSIONID"))
                .andReturn();
        assertThat(login.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .filteredOn(value -> value.startsWith("XSRF-TOKEN="))
                .hasSize(1);
        Cookie sessionCookie = login.getResponse().getCookie("JSESSIONID");
        assertThat(sessionCookie).isNotNull();

        Cookie freshCsrf = fetchCsrfCookie(sessionCookie);
        assertThat(freshCsrf.getValue()).isNotEqualTo(preLoginCsrf.getValue());
    }

    @Test
    void loginProfileRenameAndLogoutUseServerSessionAndCsrf() throws Exception {
        register("angler@example.com", "strong-pass", "Wall_E");
        Cookie loginCsrf = fetchCsrfCookie();

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .cookie(loginCsrf)
                        .header("X-XSRF-TOKEN", loginCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"angler@example.com\",\"password\":\"strong-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("JSESSIONID"))
                .andReturn();
        Cookie sessionCookie = login.getResponse().getCookie("JSESSIONID");
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie.getSecure()).isTrue();
        assertThat(sessionCookie.getAttribute("SameSite")).isEqualToIgnoringCase("Lax");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                Integer.class,
                "angler@example.com")).isEqualTo(1);

        byte[] serializedContext = jdbcTemplate.queryForObject(
                """
                SELECT ATTRIBUTE_BYTES
                FROM SPRING_SESSION_ATTRIBUTES
                WHERE ATTRIBUTE_NAME = 'SPRING_SECURITY_CONTEXT'
                """,
                byte[].class);
        String serializedText = new String(serializedContext, StandardCharsets.ISO_8859_1);
        assertThat(serializedText)
                .doesNotContain("strong-pass")
                .doesNotContain("{bcrypt-sha256}");

        mvc.perform(get("/api/v1/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Wall_E"));

        mvc.perform(patch("/api/v1/me")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"River\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        Cookie authenticatedCsrf = fetchCsrfCookie(sessionCookie);
        mvc.perform(patch("/api/v1/me")
                        .cookie(sessionCookie, authenticatedCsrf)
                        .header("X-XSRF-TOKEN", authenticatedCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"River\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("River"));

        mvc.perform(post("/api/v1/auth/logout")
                        .cookie(sessionCookie, authenticatedCsrf)
                        .header("X-XSRF-TOKEN", authenticatedCsrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        mvc.perform(get("/api/v1/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void loginRotatesAnExistingSessionId() throws Exception {
        register("angler@example.com", "strong-pass", "Wall_E");
        Cookie existingSessionCookie = createPersistedSessionCookie();
        Cookie loginCsrf = fetchCsrfCookie(existingSessionCookie);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION", Integer.class)).isEqualTo(1);

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .cookie(existingSessionCookie, loginCsrf)
                        .header("X-XSRF-TOKEN", loginCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"angler@example.com\",\"password\":\"strong-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("JSESSIONID"))
                .andReturn();

        Cookie authenticatedSessionCookie = login.getResponse().getCookie("JSESSIONID");
        assertThat(authenticatedSessionCookie.getValue())
                .isNotEqualTo(existingSessionCookie.getValue());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION", Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidCredentialsReturn401AndStableCode() throws Exception {
        register("angler@example.com", "strong-pass", "Wall_E");
        Cookie csrfCookie = fetchCsrfCookie();

        mvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"angler@example.com\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void invalidRegistrationReturnsFieldErrorsWithoutCreatingAUser() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();
        mvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    private void register(String email, String password, String nickname) throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterRequest(email, password, nickname));
        Cookie csrfCookie = fetchCsrfCookie();
        mvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private Cookie fetchCsrfCookie(Cookie... requestCookies) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/auth/csrf");
        if (requestCookies.length > 0) {
            request.cookie(requestCookies);
        }
        MvcResult csrf = mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }

    private Cookie createPersistedSessionCookie() {
        Session session = createAndSaveSession(sessionRepository);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookieSerializer.writeCookieValue(new CookieValue(request, response, session.getId()));

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        MockCookie cookie = MockCookie.parse(setCookie);
        assertThat(cookie.getName()).isEqualTo("JSESSIONID");
        return cookie;
    }

    private static <S extends Session> S createAndSaveSession(SessionRepository<S> repository) {
        S session = repository.createSession();
        repository.save(session);
        return session;
    }
}
