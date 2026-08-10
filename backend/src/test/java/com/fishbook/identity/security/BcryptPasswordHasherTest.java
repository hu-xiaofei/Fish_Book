package com.fishbook.identity.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.CharacterCodingException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BcryptPasswordHasherTest {

    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher();

    @Test
    void hashesAndMatchesUsingVersionedBcryptSha256Format() {
        String encoded = hasher.hash("strong-pass");

        assertThat(encoded).startsWith("{bcrypt-sha256}");
        assertThat(encoded).hasSizeLessThanOrEqualTo(100);
        assertThat(hasher.matches("strong-pass", encoded)).isTrue();
        assertThat(hasher.matches("wrong-pass", encoded)).isFalse();
        assertThat(hasher.matches("strong-pass", "$2a$10$not-the-versioned-format"))
                .isFalse();
    }

    @Test
    void hashesAndMatchesOneHundredTwentyEightCodeUnitsIncludingSurrogatePair() {
        String password = "鱼".repeat(126) + "🐟";

        assertThat(password).hasSize(128);

        String encoded = hasher.hash(password);

        assertThat(hasher.matches(password, encoded)).isTrue();
    }

    @Test
    void distinguishesPasswordsThatDifferAfterUtf8ByteSeventyTwo() {
        String sharedFirstSeventyTwoBytes = "a".repeat(72);
        String first = sharedFirstSeventyTwoBytes + "x";
        String second = sharedFirstSeventyTwoBytes + "y";

        String firstHash = hasher.hash(first);
        String secondHash = hasher.hash(second);

        assertThat(hasher.matches(first, firstHash)).isTrue();
        assertThat(hasher.matches(second, secondHash)).isTrue();
        assertThat(hasher.matches(first, secondHash)).isFalse();
        assertThat(hasher.matches(second, firstHash)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("malformedPasswords")
    void rejectsMalformedUtf16InsteadOfReplacingIt(String password) {
        String validHash = hasher.hash("strong-pass");

        assertThatThrownBy(() -> hasher.hash(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password cannot be encoded as UTF-8")
                .hasCauseInstanceOf(CharacterCodingException.class);
        assertThatThrownBy(() -> hasher.matches(password, validHash))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password cannot be encoded as UTF-8")
                .hasCauseInstanceOf(CharacterCodingException.class);
    }

    private static Stream<String> malformedPasswords() {
        return Stream.of(
                "a".repeat(9) + "\uD800",
                "a".repeat(9) + "\uD801",
                "a".repeat(9) + "\uDC00");
    }
}
