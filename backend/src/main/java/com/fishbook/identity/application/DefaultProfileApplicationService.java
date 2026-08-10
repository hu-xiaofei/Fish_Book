package com.fishbook.identity.application;

import com.fishbook.identity.domain.InvalidNicknameException;
import com.fishbook.identity.domain.User;
import com.fishbook.identity.domain.UserNotFoundException;
import com.fishbook.identity.domain.UserRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultProfileApplicationService implements ProfileApplicationService {

    private static final int MAX_NICKNAME_LENGTH = 50;

    private final UserRepository userRepository;
    private final Clock clock;

    @Autowired
    public DefaultProfileApplicationService(UserRepository userRepository) {
        this(userRepository, Clock.systemUTC());
    }

    public DefaultProfileApplicationService(UserRepository userRepository, Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public UserView currentUser(String normalizedEmail) {
        return toView(findUser(normalizedEmail));
    }

    @Override
    @Transactional
    public UserView updateNickname(String normalizedEmail, String nickname) {
        if (nickname == null || nickname.isBlank() || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new InvalidNicknameException();
        }

        User current = findUser(normalizedEmail);
        User renamed = User.reconstitute(
                current.id(),
                current.email(),
                current.passwordHash(),
                nickname,
                current.role(),
                current.status(),
                current.createdAt(),
                clock.instant());
        return toView(userRepository.save(renamed));
    }

    private User findUser(String normalizedEmail) {
        Objects.requireNonNull(normalizedEmail, "normalizedEmail must not be null");
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException(normalizedEmail));
    }

    private static UserView toView(User user) {
        return new UserView(user.id(), user.email(), user.nickname(), user.role().name());
    }
}
