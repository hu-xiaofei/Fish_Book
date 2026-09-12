package com.fishbook.media.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.persistence.DisabledMediaStore;
import io.minio.MinioClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

class MediaConfigurationTest {
    private static final List<String> VALID_ENABLED_PROPERTIES = List.of(
            "fishbook.media.enabled=true",
            "fishbook.media.endpoint=http://localhost:9000",
            "fishbook.media.access-key=test-access",
            "fishbook.media.secret-key=test-secret",
            "fishbook.media.bucket=fishbook-test");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MediaConfiguration.class, MinioConfiguration.class);

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
                    assertThat(context).hasSingleBean(MediaStore.class);
                    assertThat(context).hasSingleBean(MinioClient.class);
                    assertThat(context).doesNotHaveBean(DisabledMediaStore.class);
                });
    }

    @Test
    void explicitMinioKeepsOneStore() {
        contextRunner
                .withPropertyValues(VALID_ENABLED_PROPERTIES.toArray(String[]::new))
                .withPropertyValues("fishbook.media.provider=minio")
                .run(context -> {
                    assertThat(context).hasSingleBean(MediaStore.class);
                    assertThat(context).hasSingleBean(MinioClient.class);
                });
    }

    @Test
    void disabledOssDoesNotCreateMinio() {
        contextRunner
                .withPropertyValues("fishbook.media.enabled=false", "fishbook.media.provider=oss")
                .run(context -> {
                    assertThat(context).hasSingleBean(MediaStore.class);
                    assertThat(context.getBean(MediaStore.class)).isInstanceOf(DisabledMediaStore.class);
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                });
    }

    @Test
    void componentScanWithDisabledMediaDoesNotCreateMinio() {
        new ApplicationContextRunner()
                .withInitializer(context -> new ClassPathBeanDefinitionScanner(
                        (BeanDefinitionRegistry) context.getBeanFactory())
                        .scan("com.fishbook.media.config"))
                .withPropertyValues(VALID_ENABLED_PROPERTIES.toArray(String[]::new))
                .withPropertyValues("fishbook.media.enabled=false", "fishbook.media.provider=minio")
                .run(context -> {
                    assertThat(context).hasSingleBean(MediaStore.class);
                    assertThat(context.getBean(MediaStore.class)).isInstanceOf(DisabledMediaStore.class);
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                });
    }

    @Test
    void unknownProviderFails() {
        contextRunner
                .withPropertyValues(VALID_ENABLED_PROPERTIES.toArray(String[]::new))
                .withPropertyValues("fishbook.media.provider=unexpected")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledOssDoesNotRequireMinioCredentialsOrCreateMinio() {
        contextRunner
                .withPropertyValues("fishbook.media.enabled=true", "fishbook.media.provider=oss",
                        "fishbook.media.bucket=fishbook-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                });
    }

    @Test
    void enabledOssDoesNotCreateMinioEvenWithLegacyCredentials() {
        contextRunner
                .withPropertyValues(VALID_ENABLED_PROPERTIES.toArray(String[]::new))
                .withPropertyValues("fishbook.media.provider=oss")
                .run(context -> assertThat(context).doesNotHaveBean(MinioClient.class));
    }

    @Test
    void enabledOssStillRequiresBucket() {
        contextRunner
                .withPropertyValues("fishbook.media.enabled=true", "fishbook.media.provider=oss",
                        "fishbook.media.bucket=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void omittedEnabledFlagUsesUnavailableStore() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MediaStore.class);
            assertThat(context.getBean(MediaStore.class)).isInstanceOf(DisabledMediaStore.class);
            assertThat(context).doesNotHaveBean(MinioClient.class);
        });
    }

    @Test
    void mediaPropertiesDescriptionDoesNotRevealConfiguration() {
        var properties = new MediaProperties(true, "http://localhost:9000", "test-access",
                "test-secret", "fishbook-test");

        assertThat(properties.toString()).doesNotContain("http://localhost", "test-access",
                "test-secret", "fishbook-test");
    }

    @Test
    void directNullProviderUsesCompatibleMinioDefault() {
        var properties = new MediaProperties(true, "http://localhost:9000", "test-access",
                "test-secret", "fishbook-test", null);

        assertThat(properties.provider()).isEqualTo(MediaProvider.MINIO);
        assertThat(properties.isValidWhenEnabled()).isTrue();
    }

    @Test
    void fiveArgumentConstructorPreservesMinioDefaultAndValidation() {
        var properties = new MediaProperties(true, "http://localhost:9000", "test-access",
                "test-secret", "fishbook-test");

        assertThat(properties.provider()).isEqualTo(MediaProvider.MINIO);
        assertThat(properties.isValidWhenEnabled()).isTrue();
        assertThat(new MediaProperties(true, null, null, null, "fishbook-test")
                .isValidWhenEnabled()).isFalse();
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
