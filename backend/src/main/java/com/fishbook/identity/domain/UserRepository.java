package com.fishbook.identity.domain;

import java.util.Optional;

public interface UserRepository {
    boolean existsByEmail(String normalizedEmail);

    Optional<User> findByEmail(String normalizedEmail);

    User save(User user);
}
