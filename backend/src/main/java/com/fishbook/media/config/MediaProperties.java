package com.fishbook.media.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fishbook.media")
public record MediaProperties(
        boolean enabled,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket) {

    @AssertTrue(message = "endpoint, access-key, secret-key and bucket are required when media is enabled")
    public boolean isValidWhenEnabled() {
        return !enabled
                || (hasText(endpoint) && hasText(accessKey) && hasText(secretKey) && hasText(bucket));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
