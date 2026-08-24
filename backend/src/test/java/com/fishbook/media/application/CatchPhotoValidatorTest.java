package com.fishbook.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CatchPhotoValidatorTest {
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    private final CatchPhotoValidator validator = new CatchPhotoValidator();

    @Test
    void acceptsJpegAndReturnsCanonicalType() {
        var content = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};

        assertThat(validator.validate(content, " IMAGE/JPEG ")).isEqualTo(CatchPhotoType.JPEG);
    }

    @Test
    void acceptsPngAndReturnsCanonicalType() {
        var content = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
        };

        assertThat(validator.validate(content, "image/png")).isEqualTo(CatchPhotoType.PNG);
    }

    @Test
    void acceptsWebpAndReturnsCanonicalType() {
        var content = new byte[] {
                0x52, 0x49, 0x46, 0x46, 0x04, 0x00, 0x00, 0x00,
                0x57, 0x45, 0x42, 0x50, 0x00
        };

        assertThat(validator.validate(content, "image/webp")).isEqualTo(CatchPhotoType.WEBP);
    }

    @Test
    void acceptsExactlyTenMegabytes() {
        var content = new byte[MAX_BYTES];
        Arrays.fill(content, (byte) 0x01);
        content[0] = (byte) 0xff;
        content[1] = (byte) 0xd8;
        content[2] = (byte) 0xff;

        assertThat(validator.validate(content, "image/jpeg")).isEqualTo(CatchPhotoType.JPEG);
    }

    @Test
    void rejectsDeclaredTypeThatDoesNotMatchMagicBytes() {
        var jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff};

        assertInvalid(() -> validator.validate(jpeg, "image/png"));
    }

    @Test
    void rejectsEmptyContent() {
        assertInvalid(() -> validator.validate(new byte[0], "image/jpeg"));
    }

    @Test
    void rejectsUnsupportedGifEvenWhenDeclarationMatches() {
        var gif = new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};

        assertInvalid(() -> validator.validate(gif, "image/gif"));
    }

    @Test
    void rejectsContentLargerThanTenMegabytes() {
        var content = new byte[MAX_BYTES + 1];
        content[0] = (byte) 0xff;
        content[1] = (byte) 0xd8;
        content[2] = (byte) 0xff;

        assertInvalid(() -> validator.validate(content, "image/jpeg"));
    }

    private static void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(InvalidCatchPhotoException.class)
                .isInstanceOfSatisfying(
                        InvalidCatchPhotoException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_CATCH_PHOTO"));
    }
}
