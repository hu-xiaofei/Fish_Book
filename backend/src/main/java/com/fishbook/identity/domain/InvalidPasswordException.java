package com.fishbook.identity.domain;

public final class InvalidPasswordException extends RuntimeException {

    private static final String CODE = "INVALID_PASSWORD";

    public InvalidPasswordException() {
        super("Password must contain between 10 and 128 characters");
    }

    public String code() {
        return CODE;
    }
}
