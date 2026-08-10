package com.fishbook.identity.security;

import static com.fishbook.common.error.GlobalExceptionHandler.requestId;

import com.fishbook.common.error.ApiErrorResponse;
import com.fishbook.identity.domain.PasswordHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    @Bean
    DefaultCookieSerializerCustomizer sessionCookieSerializerCustomizer(
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String name,
            @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        return serializer -> {
            serializer.setCookieName(name);
            serializer.setUseHttpOnlyCookie(httpOnly);
            serializer.setSameSite(sameSite);
            serializer.setUseSecureCookie(secure);
        };
    }

    @Bean
    PasswordEncoder passwordEncoder(PasswordHasher passwordHasher) {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return passwordHasher.hash(rawPassword.toString());
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return passwordHasher.matches(rawPassword.toString(), encodedPassword);
            }
        };
    }

    @Bean
    AuthenticationManager authenticationManager(
            DatabaseUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SessionAuthenticationStrategy loginSessionAuthenticationStrategy(
            CookieCsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            HttpSessionSecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            ObjectMapper objectMapper) throws Exception {
        RequestMatcher authenticationRequiredEndpoints = new OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern("/api/v1/me/**"),
                PathPatternRequestMatcher.pathPattern("/api/v1/auth/logout"));
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) -> {
            if (exception instanceof CsrfException) {
                writeError(
                        objectMapper,
                        request,
                        response,
                        HttpStatus.FORBIDDEN,
                        "CSRF_INVALID",
                        "CSRF token is missing or invalid");
                return;
            }
            writeError(
                    objectMapper,
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "Access is denied");
        };

        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/csrf")
                        .permitAll()
                        .requestMatchers(authenticationRequiredEndpoints)
                        .authenticated()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf
                        .spa()
                        .csrfTokenRepository(csrfTokenRepository))
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .requestCache(requestCache -> requestCache.disable())
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            if (authenticationRequiredEndpoints.matches(request)) {
                                writeError(
                                        objectMapper,
                                        request,
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "AUTHENTICATION_REQUIRED",
                                        "Authentication is required");
                                return;
                            }
                            writeError(
                                    objectMapper,
                                    request,
                                    response,
                                    HttpStatus.FORBIDDEN,
                                    "ACCESS_DENIED",
                                    "Access is denied");
                        })
                        .accessDeniedHandler(accessDeniedHandler))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(
                                HttpStatus.NO_CONTENT)))
                .build();
    }

    private static void writeError(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                code,
                message,
                List.of(),
                requestId(request)));
    }
}
