package com.fishbook.identity.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    void hashesAndMatchesOneHundredTwentyEightMultibyteCharacters() {
        String password = "鱼".repeat(128);

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
}
