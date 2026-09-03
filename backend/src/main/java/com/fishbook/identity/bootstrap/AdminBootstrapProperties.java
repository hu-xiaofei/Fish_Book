package com.fishbook.identity.bootstrap;

import jakarta.validation.constraints.AssertTrue;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fishbook.admin.bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String nickname) {

    @AssertTrue(message = "email, password and nickname are required when admin bootstrap is enabled")
    public boolean isValidWhenEnabled() {
        return !enabled || Stream.of(email, password, nickname)
                .allMatch(value -> value != null && !value.isBlank());
    }
}
