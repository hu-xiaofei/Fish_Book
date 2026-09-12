package com.fishbook.media.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fishbook.media")
public record MediaProperties(
        boolean enabled,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        @DefaultValue("minio") MediaProvider provider) {

    @ConstructorBinding
    public MediaProperties {
        if (provider == null) {
            provider = MediaProvider.MINIO;
        }
    }

    public MediaProperties(
            boolean enabled, String endpoint, String accessKey, String secretKey, String bucket) {
        this(enabled, endpoint, accessKey, secretKey, bucket, MediaProvider.MINIO);
    }

    @AssertTrue(message = "required media configuration is missing")
    public boolean isValidWhenEnabled() {
        return !enabled
                || hasText(bucket) && (provider == MediaProvider.OSS
                        || hasText(endpoint) && hasText(accessKey) && hasText(secretKey));
    }

    @Override
    public String toString() {
        return "MediaProperties[configuration=redacted]";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
