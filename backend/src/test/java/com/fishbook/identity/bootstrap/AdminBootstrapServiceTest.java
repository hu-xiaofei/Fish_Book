package com.fishbook.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fishbook.identity.domain.InvalidEmailException;
import com.fishbook.identity.domain.InvalidNicknameException;
import com.fishbook.identity.domain.InvalidPasswordException;
import com.fishbook.identity.domain.PasswordHasher;
import com.fishbook.identity.domain.User;
import com.fishbook.identity.domain.UserRepository;
import com.fishbook.identity.domain.UserRole;
import com.fishbook.identity.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdminBootstrapServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    private FakeUserRepository repository;
    private FakePasswordHasher passwordHasher;
    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        repository = new FakeUserRepository();
        passwordHasher = new FakePasswordHasher();
        service = new AdminBootstrapService(
                repository,
                passwordHasher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAnActiveAdministratorWithNormalizedEmailAndHashedPassword() {
        service.bootstrap(new AdminBootstrapProperties(
                true, " Admin@Example.COM ", "strong-admin-pass", "管理员"));

        User saved = repository.savedUser();
        assertThat(saved.email()).isEqualTo("admin@example.com");
        assertThat(saved.role()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.passwordHash()).isEqualTo("hashed:strong-admin-pass");
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(repository.saveCount()).isEqualTo(1);
        assertThat(passwordHasher.hashCount()).isEqualTo(1);
    }

    @Test
    void disabledBootstrapDoesNotValidateOrAccessTheRepository() {
        service.bootstrap(new AdminBootstrapProperties(false, null, null, null));

        assertThat(repository.lookupCount()).isZero();
        assertThat(repository.saveCount()).isZero();
        assertThat(passwordHasher.hashCount()).isZero();
    }

    @Test
    void existingAdministratorMakesBootstrapANoOp() {
        repository.put(existingUser(UserRole.ADMIN));

        service.bootstrap(validProperties());

        assertThat(repository.saveCount()).isZero();
        assertThat(passwordHasher.hashCount()).isZero();
    }

    @Test
    void refusesToPromoteAnExistingUser() {
        repository.put(existingUser(UserRole.USER));

        assertThatThrownBy(() -> service.bootstrap(validProperties()))
                .isInstanceOf(AdminBootstrapConflictException.class)
                .hasMessageNotContaining("strong-admin-pass");
        assertThat(repository.saveCount()).isZero();
        assertThat(passwordHasher.hashCount()).isZero();
    }

    @Test
    void acceptsTheRegistrationInputBoundaries() {
        service.bootstrap(new AdminBootstrapProperties(
                true, "a".repeat(320), "a".repeat(10), "a".repeat(50)));
        service.bootstrap(new AdminBootstrapProperties(
                true, "second@example.com", "a".repeat(128), "管理员"));

        assertThat(repository.saveCount()).isEqualTo(2);
        assertThat(passwordHasher.hashCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("invalidProperties")
    void rejectsInvalidInputsBeforeRepositoryAccess(AdminBootstrapProperties properties,
            Class<? extends RuntimeException> exceptionType) {
        assertThatThrownBy(() -> service.bootstrap(properties))
                .isInstanceOf(exceptionType);

        assertThat(repository.lookupCount()).isZero();
        assertThat(repository.saveCount()).isZero();
        assertThat(passwordHasher.hashCount()).isZero();
    }

    private static AdminBootstrapProperties validProperties() {
        return new AdminBootstrapProperties(
                true, "admin@example.com", "strong-admin-pass", "管理员");
    }

    private static User existingUser(UserRole role) {
        return User.reconstitute(
                17L,
                "admin@example.com",
                "existing-password-hash",
                "Existing",
                role,
                UserStatus.ACTIVE,
                NOW,
                NOW);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> invalidProperties() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        new AdminBootstrapProperties(true, " ", "strong-admin-pass", "管理员"),
                        InvalidEmailException.class),
                org.junit.jupiter.params.provider.Arguments.of(
                        new AdminBootstrapProperties(true, null, "strong-admin-pass", "管理员"),
                        InvalidEmailException.class),
                org.junit.jupiter.params.provider.Arguments.of(
                        new AdminBootstrapProperties(true, "a".repeat(321), "strong-admin-pass", "管理员"),
                        InvalidEmailException.class),
                org.junit.jupiter.params.provider.Arguments.of(
                        new AdminBootstrapProperties(true, "admin@example.com", "a".repeat(9), "管理员"),
                        InvalidPasswordException.class),
                org.junit.jupiter.params.provider.Arguments.of(
                        new AdminBootstrapProperties(true, "admin@example.com", null, "管理员"),
                        InvalidPasswordException.class),
                org.junit.jupiter.params.provider.Arguments.of(
                        new AdminBootstrapProperties(true, "admin@example.com", "a".repeat(129), "管理员"),
                        InvalidPasswordException.class),
                org.junit.jupiter.params.provider.Arguments.of(
                        new AdminBootstrapProperties(true, "admin@example.com", "a".repeat(9) + "\uD800", "管理员"),
                        InvalidPasswordException.class),
                org.junit.jupiter.params.provider.Arguments.of(
                        new AdminBootstrapProperties(true, "admin@example.com", "strong-admin-pass", " "),
                        InvalidNicknameException.class),
                org.junit.jupiter.params.provider.Arguments.of(
                        new AdminBootstrapProperties(true, "admin@example.com", "strong-admin-pass", null),
                        InvalidNicknameException.class),
                org.junit.jupiter.params.provider.Arguments.of(
                        new AdminBootstrapProperties(true, "admin@example.com", "strong-admin-pass", "a".repeat(51)),
                        InvalidNicknameException.class));
    }

    private static final class FakeUserRepository implements UserRepository {

        private final Map<String, User> usersByEmail = new HashMap<>();
        private User savedUser;
        private int lookupCount;
        private int saveCount;

        @Override
        public boolean existsByEmail(String normalizedEmail) {
            return usersByEmail.containsKey(normalizedEmail);
        }

        @Override
        public Optional<User> findByEmail(String normalizedEmail) {
            lookupCount++;
            return Optional.ofNullable(usersByEmail.get(normalizedEmail));
        }

        @Override
        public User save(User user) {
            saveCount++;
            savedUser = User.reconstitute(
                    1L,
                    user.email(),
                    user.passwordHash(),
                    user.nickname(),
                    user.role(),
                    user.status(),
                    user.createdAt(),
                    user.updatedAt());
            usersByEmail.put(savedUser.email(), savedUser);
            return savedUser;
        }

        void put(User user) {
            usersByEmail.put(user.email(), user);
        }

        User savedUser() {
            return savedUser;
        }

        int lookupCount() {
            return lookupCount;
        }

        int saveCount() {
            return saveCount;
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {

        private int hashCount;

        @Override
        public String hash(String rawPassword) {
            hashCount++;
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("hashed:" + rawPassword);
        }

        int hashCount() {
            return hashCount;
        }
    }
}
