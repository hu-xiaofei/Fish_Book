package com.fishbook.media.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OssPropertiesTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "https://oss-cn-hangzhou-internal.aliyuncs.com",
            "https://example.com/",
            "HTTPS://example.com"
    })
    void acceptsHttpsOriginWithRegionAndRole(String endpoint) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(
                    new OssProperties(endpoint, "cn-hangzhou", "test-role"))).isEmpty();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "http://localhost", "https://", "https:///example.com",
            "https://example.com?token=x", "https://user@example.com",
            "https://example.com#fragment", "https://example.com/bucket", "not a uri"
    })
    void rejectsEndpointThatIsNotAnHttpsOrigin(String endpoint) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(
                    new OssProperties(endpoint, "cn-hangzhou", "test-role"))).isNotEmpty();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingRegion(String region) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(
                    new OssProperties("https://example.com", region, "test-role"))).isNotEmpty();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingRole(String roleName) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(
                    new OssProperties("https://example.com", "cn-hangzhou", roleName))).isNotEmpty();
        }
    }
}
