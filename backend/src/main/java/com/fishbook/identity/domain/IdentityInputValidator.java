package com.fishbook.identity.domain;

import java.util.Locale;

public final class IdentityInputValidator {

    private static final int MAX_EMAIL_LENGTH = 320;
    private static final int MIN_PASSWORD_LENGTH = 10;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_NICKNAME_LENGTH = 50;

    private IdentityInputValidator() {}

    public static String normalizeAndValidateEmail(String email) {
        if (email == null) {
            throw new InvalidEmailException();
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank() || normalizedEmail.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidEmailException();
        }
        return normalizedEmail;
    }

    public static String validatePassword(String password) {
        if (password == null
                || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH
                || containsUnpairedSurrogate(password)) {
            throw new InvalidPasswordException();
        }
        return password;
    }

    public static String validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank() || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new InvalidNicknameException();
        }
        return nickname;
    }

    private static boolean containsUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }
}
