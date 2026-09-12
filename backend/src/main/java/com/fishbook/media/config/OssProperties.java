package com.fishbook.media.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fishbook.media.oss")
public record OssProperties(
        @NotBlank String endpoint,
        @NotBlank String region,
        @NotBlank String roleName) {

    @AssertTrue(message = "OSS endpoint must be an HTTPS origin")
    public boolean isHttpsEndpoint() {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            var uri = URI.create(endpoint);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
