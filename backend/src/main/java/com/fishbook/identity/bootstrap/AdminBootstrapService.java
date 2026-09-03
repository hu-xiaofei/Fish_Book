package com.fishbook.identity.bootstrap;

import com.fishbook.identity.domain.IdentityInputValidator;
import com.fishbook.identity.domain.PasswordHasher;
import com.fishbook.identity.domain.User;
import com.fishbook.identity.domain.UserRepository;
import com.fishbook.identity.domain.UserRole;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class AdminBootstrapService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public AdminBootstrapService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void bootstrap(AdminBootstrapProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        if (!properties.enabled()) {
            return;
        }

        String normalizedEmail = IdentityInputValidator.normalizeAndValidateEmail(properties.email());
        String password = IdentityInputValidator.validatePassword(properties.password());
        String nickname = IdentityInputValidator.validateNickname(properties.nickname());

        var existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            if (existing.get().role() == UserRole.ADMIN) {
                return;
            }
            throw new AdminBootstrapConflictException(normalizedEmail);
        }

        String passwordHash = passwordHasher.hash(password);
        userRepository.save(User.initializeAdmin(normalizedEmail, passwordHash, nickname, clock.instant()));
    }
}
