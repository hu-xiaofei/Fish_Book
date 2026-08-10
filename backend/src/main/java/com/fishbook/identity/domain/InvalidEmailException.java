package com.fishbook.identity.domain;

public final class InvalidEmailException extends RuntimeException {

    private static final String CODE = "INVALID_EMAIL";

    public InvalidEmailException() {
        super("Email must contain between 1 and 320 characters after normalization");
    }

    public String code() {
        return CODE;
    }
}
