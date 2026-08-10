package com.fishbook.identity.domain;

import java.time.Instant;
import java.util.Objects;

public record User(
        Long id,
        String email,
        String passwordHash,
        String nickname,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public User {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(nickname, "nickname must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static User register(
            String normalizedEmail,
            String passwordHash,
            String nickname,
            Instant now) {
        return new User(
                null,
                normalizedEmail,
                passwordHash,
                nickname,
                UserRole.USER,
                UserStatus.ACTIVE,
                now,
                now);
    }

    public static User reconstitute(
            Long id,
            String normalizedEmail,
            String passwordHash,
            String nickname,
            UserRole role,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt) {
        return new User(
                id,
                normalizedEmail,
                passwordHash,
                nickname,
                role,
                status,
                createdAt,
                updatedAt);
    }
}
