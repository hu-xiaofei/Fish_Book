package com.fishbook.media.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.persistence.DisabledMediaStore;
import io.minio.MinioClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MediaConfigurationTest {
    private static final List<String> VALID_ENABLED_PROPERTIES = List.of(
            "fishbook.media.enabled=true",
            "fishbook.media.endpoint=http://localhost:9000",
            "fishbook.media.access-key=test-access",
            "fishbook.media.secret-key=test-secret",
            "fishbook.media.bucket=fishbook-test");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MinioConfiguration.class);

    @Test
    void disabledMediaUsesUnavailableStoreWithoutCreatingMinioClient() {
        contextRunner
                .withPropertyValues("fishbook.media.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(MediaStore.class);
                    assertThat(context.getBean(MediaStore.class)).isInstanceOf(DisabledMediaStore.class);
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                });
    }

    @Test
    void enabledMediaCreatesMinioClientWithoutContactingServer() {
        contextRunner
                .withPropertyValues(VALID_ENABLED_PROPERTIES.toArray(String[]::new))
                .run(context -> {
                    assertThat(context).hasSingleBean(MinioClient.class);
                    assertThat(context).doesNotHaveBean(DisabledMediaStore.class);
                });
    }

    @Test
    void enabledMediaRejectsBlankEndpoint() {
        assertBlankEnabledPropertyFails("fishbook.media.endpoint=");
    }

    @Test
    void enabledMediaRejectsBlankAccessKey() {
        assertBlankEnabledPropertyFails("fishbook.media.access-key=");
    }

    @Test
    void enabledMediaRejectsBlankSecretKey() {
        assertBlankEnabledPropertyFails("fishbook.media.secret-key=");
    }

    @Test
    void enabledMediaRejectsBlankBucket() {
        assertBlankEnabledPropertyFails("fishbook.media.bucket=");
    }

    private void assertBlankEnabledPropertyFails(String blankProperty) {
        contextRunner
                .withPropertyValues(VALID_ENABLED_PROPERTIES.toArray(String[]::new))
                .withPropertyValues(blankProperty)
                .run(context -> assertThat(context).hasFailed());
    }
}
