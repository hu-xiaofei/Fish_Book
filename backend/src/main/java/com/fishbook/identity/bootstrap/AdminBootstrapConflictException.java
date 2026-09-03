package com.fishbook.identity.bootstrap;

public final class AdminBootstrapConflictException extends RuntimeException {

    public AdminBootstrapConflictException(String normalizedEmail) {
        super("Cannot bootstrap administrator because a non-administrator account already uses email '"
                + normalizedEmail + "'");
    }
}
