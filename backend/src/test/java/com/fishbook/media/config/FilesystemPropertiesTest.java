package com.fishbook.media.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemPropertiesTest {
    @Test
    void acceptsAbsoluteWritableDirectory(@TempDir Path root) {
        assertThat(violations(new FilesystemProperties(root))).isZero();
    }

    @Test
    void rejectsRelativeRoot() {
        assertThat(violations(new FilesystemProperties(Path.of("photos")))).isGreaterThan(0);
    }

    @Test
    void rejectsRegularFile(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("photo-store"));

        assertThat(violations(new FilesystemProperties(file))).isGreaterThan(0);
    }

    @Test
    void rejectsMissingRoot(@TempDir Path tempDir) {
        assertThat(violations(new FilesystemProperties(tempDir.resolve("missing"))))
                .isGreaterThan(0);
    }

    @Test
    void rejectsNullRoot() {
        assertThat(violations(new FilesystemProperties(null))).isGreaterThan(0);
    }

    @Test
    void descriptionDoesNotRevealRoot(@TempDir Path root) {
        assertThat(new FilesystemProperties(root).toString()).doesNotContain(root.toString());
    }

    private int violations(FilesystemProperties properties) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(properties).size();
        }
    }
}
