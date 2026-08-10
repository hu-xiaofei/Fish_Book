package com.fishbook.identity.domain;

public final class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String normalizedEmail) {
        super("No user found with email '" + normalizedEmail + "'");
    }
}
