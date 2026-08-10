package com.fishbook.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishbook.identity.domain.User;
import com.fishbook.identity.domain.UserRepository;
import com.fishbook.support.MySqlTestConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class IdentityErrorContractTest {

    private static final String VALID_REGISTRATION = """
            {"email":"angler@example.com","password":"strong-pass","nickname":"Wall_E"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserRepository userRepository;

    @Test
    void malformedJsonReturnsStableInvalidRequestWithoutParserDetails() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"angler@example.com\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is invalid"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        assertDoesNotLeak(result, "HttpMessageNotReadableException", "Jackson", "Unexpected end");
    }

    @Test
    void invalidRegistrationReturnsCompleteStableValidationContract() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[1].field").value("nickname"))
                .andExpect(jsonPath("$.fieldErrors[1].message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[2].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[2].message").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unrelatedIntegrityFailureReturnsGenericNonLeaking500() throws Exception {
        when(userRepository.existsByEmail("angler@example.com"))
                .thenThrow(new DataIntegrityViolationException(
                        "SQL state 23000 violated fk_users_private"));

        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTRATION))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        assertDoesNotLeak(
                result,
                "DataIntegrityViolationException",
                "SQL state",
                "23000",
                "fk_users_private");
    }

    @Test
    void unexpectedFailureReturnsGenericNonLeaking500() throws Exception {
        when(userRepository.existsByEmail("angler@example.com"))
                .thenThrow(new IllegalStateException("framework-stack-secret"));

        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTRATION))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        assertDoesNotLeak(result, "IllegalStateException", "framework-stack-secret", "org.springframework");
    }

    @Test
    void staleAuthenticatedPrincipalReturnsNonLeakingUserNotFound() throws Exception {
        when(userRepository.findByEmail("deleted@example.com")).thenReturn(Optional.empty());

        MvcResult result = mvc.perform(get("/api/v1/me")
                        .with(user("deleted@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User was not found"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        assertDoesNotLeak(result, "deleted@example.com", "UserNotFoundException");
    }

    @Test
    void duplicateConstraintRaceReturnsStableConflictWithoutDatabaseDetails() throws Exception {
        when(userRepository.existsByEmail("angler@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new IllegalStateException("Duplicate entry for key 'uk_users_email'")));

        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTRATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message")
                        .value("An account with that email already exists"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        assertDoesNotLeak(result, "uk_users_email", "Duplicate entry", "could not execute statement");
    }

    private static void assertDoesNotLeak(MvcResult result, String... forbiddenFragments)
            throws Exception {
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(forbiddenFragments);
    }
}
