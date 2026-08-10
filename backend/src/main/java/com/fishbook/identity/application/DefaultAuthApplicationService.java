package com.fishbook.identity.application;

import com.fishbook.identity.domain.DuplicateEmailException;
import com.fishbook.identity.domain.InvalidEmailException;
import com.fishbook.identity.domain.InvalidNicknameException;
import com.fishbook.identity.domain.InvalidPasswordException;
import com.fishbook.identity.domain.PasswordHasher;
import com.fishbook.identity.domain.User;
import com.fishbook.identity.domain.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

@Service
public final class DefaultAuthApplicationService implements AuthApplicationService {

    private static final int MAX_EMAIL_LENGTH = 320;
    private static final int MIN_PASSWORD_LENGTH = 10;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_NICKNAME_LENGTH = 50;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Autowired
    public DefaultAuthApplicationService(
            UserRepository userRepository,
            PasswordHasher passwordHasher) {
        this(userRepository, passwordHasher, Clock.systemUTC());
    }

    public DefaultAuthApplicationService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public UserView register(RegisterUserCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        String normalizedEmail = normalizeAndValidateEmail(command.email());
        validatePassword(command.password());
        validateNickname(command.nickname());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        String passwordHash = passwordHasher.hash(command.password());
        User saved = userRepository.save(User.register(
                normalizedEmail,
                passwordHash,
                command.nickname(),
                clock.instant()));

        return new UserView(saved.id(), saved.email(), saved.nickname(), saved.role().name());
    }

    private static String normalizeAndValidateEmail(String email) {
        if (email == null) {
            throw new InvalidEmailException();
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank() || normalizedEmail.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidEmailException();
        }
        return normalizedEmail;
    }

    private static void validatePassword(String password) {
        if (password == null
                || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH
                || containsUnpairedSurrogate(password)) {
            throw new InvalidPasswordException();
        }
    }

    private static boolean containsUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }

    private static void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank() || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new InvalidNicknameException();
        }
    }
}
