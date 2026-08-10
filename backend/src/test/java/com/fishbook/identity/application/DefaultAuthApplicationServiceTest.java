package com.fishbook.identity.application;

import com.fishbook.identity.domain.DuplicateEmailException;
import com.fishbook.identity.domain.InvalidEmailException;
import com.fishbook.identity.domain.InvalidNicknameException;
import com.fishbook.identity.domain.InvalidPasswordException;
import com.fishbook.identity.domain.PasswordHasher;
import com.fishbook.identity.domain.User;
import com.fishbook.identity.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAuthApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    private FakeUserRepository repository;
    private FakePasswordHasher passwordHasher;
    private DefaultAuthApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new FakeUserRepository();
        passwordHasher = new FakePasswordHasher();
        service = new DefaultAuthApplicationService(
                repository,
                passwordHasher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersActiveUserWithNormalizedEmailAndHashedPassword() {
        UserView result = service.register(
                new RegisterUserCommand(" Angler@Example.COM ", "strong-pass", "Wall_E"));

        assertThat(result.email()).isEqualTo("angler@example.com");
        assertThat(result.nickname()).isEqualTo("Wall_E");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(repository.savedUser().passwordHash()).isEqualTo("hashed:strong-pass");
        assertThat(repository.savedUser().createdAt()).isEqualTo(NOW);
        assertThat(repository.saveCount()).isEqualTo(1);
        assertThat(passwordHasher.hashCount()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        repository.addExistingEmail("angler@example.com");

        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("ANGLER@example.com", "strong-pass", "Wall_E")))
                .isInstanceOfSatisfying(DuplicateEmailException.class,
                        exception -> assertThat(exception.code()).isEqualTo("DUPLICATE_EMAIL"));
        assertThat(repository.saveCount()).isZero();
        assertThat(passwordHasher.hashCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "                                                   "})
    void rejectsBlankEmail(String email) {
        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand(email, "strong-pass", "Wall_E")))
                .isInstanceOfSatisfying(InvalidEmailException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_EMAIL"));
    }

    @Test
    void rejectsNullEmail() {
        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand(null, "strong-pass", "Wall_E")))
                .isInstanceOfSatisfying(InvalidEmailException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_EMAIL"));
    }

    @Test
    void rejectsNormalizedEmailLongerThanThreeHundredTwentyCharacters() {
        String email = " " + "a".repeat(321) + " ";

        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand(email, "strong-pass", "Wall_E")))
                .isInstanceOf(InvalidEmailException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "123456789",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    void rejectsPasswordOutsideTenToOneHundredTwentyEightCharacters(String password) {
        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("angler@example.com", password, "Wall_E")))
                .isInstanceOfSatisfying(InvalidPasswordException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_PASSWORD"));
    }

    @Test
    void acceptsPasswordsAtTenAndOneHundredTwentyEightCharacterBoundaries() {
        assertThatCode(() -> service.register(
                new RegisterUserCommand("ten@example.com", "a".repeat(10), "Ten")))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.register(
                new RegisterUserCommand("max@example.com", "a".repeat(128), "Max")))
                .doesNotThrowAnyException();

        assertThat(passwordHasher.hashCount()).isEqualTo(2);
        assertThat(repository.saveCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("malformedPasswords")
    void rejectsPasswordsContainingUnpairedSurrogates(String password) {
        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("angler@example.com", password, "Wall_E")))
                .isInstanceOfSatisfying(InvalidPasswordException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_PASSWORD"));
        assertThat(passwordHasher.hashCount()).isZero();
        assertThat(repository.saveCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "                                                   "})
    void rejectsBlankNickname(String nickname) {
        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("angler@example.com", "strong-pass", nickname)))
                .isInstanceOfSatisfying(InvalidNicknameException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_NICKNAME"));
    }

    @Test
    void rejectsNicknameLongerThanFiftyCharacters() {
        String nickname = "a".repeat(51);
        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("angler@example.com", "strong-pass", nickname)))
                .isInstanceOf(InvalidNicknameException.class);
    }

    private static Stream<String> malformedPasswords() {
        return Stream.of(
                "a".repeat(9) + "\uD800",
                "a".repeat(9) + "\uD801",
                "a".repeat(9) + "\uDC00");
    }

    private static final class FakeUserRepository implements UserRepository {

        private final Map<String, User> usersByEmail = new HashMap<>();
        private User savedUser;
        private int saveCount;

        @Override
        public boolean existsByEmail(String normalizedEmail) {
            return usersByEmail.containsKey(normalizedEmail);
        }

        @Override
        public Optional<User> findByEmail(String normalizedEmail) {
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

        void addExistingEmail(String email) {
            usersByEmail.put(email, null);
        }

        User savedUser() {
            return savedUser;
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
