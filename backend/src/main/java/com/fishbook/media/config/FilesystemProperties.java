package com.fishbook.media.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fishbook.media.filesystem")
public record FilesystemProperties(@NotNull Path root) {
    @AssertTrue(message = "filesystem media root must be an absolute writable directory")
    public boolean isUsableRoot() {
        return root != null && root.isAbsolute() && Files.isDirectory(root)
                && Files.isReadable(root) && Files.isWritable(root);
    }

    @Override
    public String toString() {
        return "FilesystemProperties[configuration=redacted]";
    }
}
