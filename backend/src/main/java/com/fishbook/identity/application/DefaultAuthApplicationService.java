package com.fishbook.identity.application;

import com.fishbook.identity.domain.DuplicateEmailException;
import com.fishbook.identity.domain.IdentityInputValidator;
import com.fishbook.identity.domain.PasswordHasher;
import com.fishbook.identity.domain.User;
import com.fishbook.identity.domain.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;

@Service
public final class DefaultAuthApplicationService implements AuthApplicationService {

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

        String normalizedEmail = IdentityInputValidator.normalizeAndValidateEmail(command.email());
        IdentityInputValidator.validatePassword(command.password());
        IdentityInputValidator.validateNickname(command.nickname());

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

}
