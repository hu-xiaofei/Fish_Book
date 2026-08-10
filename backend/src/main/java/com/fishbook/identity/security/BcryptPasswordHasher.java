package com.fishbook.identity.security;

import com.fishbook.identity.domain.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public final class BcryptPasswordHasher implements PasswordHasher {

    private static final String FORMAT_PREFIX = "{bcrypt-sha256}";
    private static final byte[] PREHASH_DOMAIN =
            "FishBook/password/v1\0".getBytes(StandardCharsets.UTF_8);

    private final PasswordEncoder encoder;

    public BcryptPasswordHasher() {
        this.encoder = new BCryptPasswordEncoder();
    }

    @Override
    public String hash(String rawPassword) {
        return FORMAT_PREFIX + encoder.encode(preHash(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || !encodedPassword.startsWith(FORMAT_PREFIX)) {
            return false;
        }
        String bcryptHash = encodedPassword.substring(FORMAT_PREFIX.length());
        return encoder.matches(preHash(rawPassword), bcryptHash);
    }

    private static String preHash(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PREHASH_DOMAIN);
            byte[] hashed = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
