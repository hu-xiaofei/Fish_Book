package com.fishbook.identity.domain;

public final class DuplicateEmailException extends RuntimeException {

    private static final String CODE = "DUPLICATE_EMAIL";

    public DuplicateEmailException(String normalizedEmail) {
        super("A user with email '" + normalizedEmail + "' already exists");
    }

    public String code() {
        return CODE;
    }
}
