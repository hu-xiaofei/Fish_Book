package com.fishbook.identity.domain;

public final class InvalidNicknameException extends RuntimeException {

    private static final String CODE = "INVALID_NICKNAME";

    public InvalidNicknameException() {
        super("Nickname must contain between 1 and 50 non-blank characters");
    }

    public String code() {
        return CODE;
    }
}
