package com.fishbook.media.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.comm.SignVersion;
import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.persistence.DisabledMediaStore;
import com.fishbook.media.persistence.FilesystemMediaStore;
import com.fishbook.media.persistence.OssMediaStore;
import com.fishbook.media.persistence.OssRoleCredentialsProvider;
import io.minio.MinioClient;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private static final String[] VALID_OSS_PROPERTIES = {
            "fishbook.media.enabled=true", "fishbook.media.provider=oss",
            "fishbook.media.bucket=fishbook-test",
            "fishbook.media.oss.endpoint=https://127.0.0.1",
            "fishbook.media.oss.region=cn-hangzhou", "fishbook.media.oss.role-name=test-role"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MediaConfiguration.class, MinioConfiguration.class,
                    OssConfiguration.class, FilesystemConfiguration.class);

    @Test
    void disabledMediaUsesUnavailableStoreWithoutCreatingMinioClient() {
        contextRunner
                .withPropertyValues("fishbook.media.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(MediaStore.class);
                    assertThat(context.getBean(MediaStore.class)).isInstanceOf(DisabledMediaStore.class);
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                    assertThat(context).doesNotHaveBean(OSS.class);
                    assertThat(context).doesNotHaveBean(OssRoleCredentialsProvider.class);
                    assertThat(context).doesNotHaveBean(OssProperties.class);
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
                    assertThat(context).doesNotHaveBean(OSS.class);
                    assertThat(context).doesNotHaveBean(OssRoleCredentialsProvider.class);
                    assertThat(context).doesNotHaveBean(OssProperties.class);
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
                    assertThat(context).doesNotHaveBean(OSS.class);
                    assertThat(context).doesNotHaveBean(OssRoleCredentialsProvider.class);
                    assertThat(context).doesNotHaveBean(OssProperties.class);
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
                    assertThat(context).doesNotHaveBean(OSS.class);
                    assertThat(context).doesNotHaveBean(OssRoleCredentialsProvider.class);
                    assertThat(context).doesNotHaveBean(OssProperties.class);
                });
    }

    @Test
    void componentScanWithDisabledMediaDoesNotCreateMinio() {
        new ApplicationContextRunner()
                .withInitializer(context -> new ClassPathBeanDefinitionScanner(
                        (BeanDefinitionRegistry) context.getBeanFactory(), true, context.getEnvironment())
                        .scan("com.fishbook.media.config"))
                .withPropertyValues(VALID_ENABLED_PROPERTIES.toArray(String[]::new))
                .withPropertyValues("fishbook.media.enabled=false", "fishbook.media.provider=minio")
                .run(context -> {
                    assertThat(context).hasSingleBean(MediaStore.class);
                    assertThat(context.getBean(MediaStore.class)).isInstanceOf(DisabledMediaStore.class);
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                    assertThat(context).doesNotHaveBean(OSS.class);
                    assertThat(context).doesNotHaveBean(OssRoleCredentialsProvider.class);
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
        var gets = new AtomicInteger();
        var provider = new OssRoleCredentialsProvider(() -> {
            gets.incrementAndGet();
            throw new IllegalStateException("test must not access IMDS");
        }, () -> {});
        contextRunner
                .withBean(OssRoleCredentialsProvider.class, () -> provider)
                .withPropertyValues(VALID_OSS_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MediaStore.class);
                    assertThat(context.getBean(MediaStore.class)).isInstanceOf(OssMediaStore.class);
                    assertThat(context).hasSingleBean(OSS.class);
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                    assertThat(gets.get()).isZero();
                    var client = (OSSClient) context.getBean(OSS.class);
                    assertThat(client.getEndpoint().toString()).isEqualTo("https://127.0.0.1");
                    var configuration = client.getClientConfiguration();
                    assertThat(configuration.getSignatureVersion()).isEqualTo(SignVersion.V4);
                    assertThat(configuration.getConnectionTimeout()).isEqualTo(3000);
                    assertThat(configuration.getSocketTimeout()).isEqualTo(10000);
                    assertThat(configuration.getMaxErrorRetry()).isEqualTo(2);
                    assertThat(configuration.isVerifySSLEnable()).isTrue();
                });
    }

    @Test
    void enabledOssDoesNotCreateMinioEvenWithLegacyCredentials() {
        contextRunner
                .withPropertyValues(VALID_ENABLED_PROPERTIES.toArray(String[]::new))
                .withPropertyValues(VALID_OSS_PROPERTIES)
                .run(context -> assertThat(context).doesNotHaveBean(MinioClient.class));
    }

    @Test
    void enabledFilesystemCreatesOnlyFilesystemStore(@TempDir Path root) {
        contextRunner
                .withPropertyValues(
                        "fishbook.media.enabled=true",
                        "fishbook.media.provider=filesystem",
                        "fishbook.media.filesystem.root=" + root)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MediaStore.class);
                    assertThat(context.getBean(MediaStore.class))
                            .isInstanceOf(FilesystemMediaStore.class);
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                    assertThat(context).doesNotHaveBean(OSS.class);
                    assertThat(context).doesNotHaveBean(OssRoleCredentialsProvider.class);
                });
    }

    @Test
    void enabledFilesystemFailsWhenRootDoesNotExist(@TempDir Path tempDir) {
        contextRunner
                .withPropertyValues(
                        "fishbook.media.enabled=true",
                        "fishbook.media.provider=filesystem",
                        "fishbook.media.filesystem.root=" + tempDir.resolve("missing"))
                .run(context -> assertThat(context).hasFailed());
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
            assertThat(context).doesNotHaveBean(OSS.class);
            assertThat(context).doesNotHaveBean(OssRoleCredentialsProvider.class);
            assertThat(context).doesNotHaveBean(OssProperties.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"fishbook.media.oss.endpoint=", "fishbook.media.oss.region=",
            "fishbook.media.oss.role-name=", "fishbook.media.oss.endpoint=http://127.0.0.1"})
    void enabledOssRejectsInvalidOrMissingConfiguration(String invalidProperty) {
        contextRunner.withPropertyValues(VALID_OSS_PROPERTIES)
                .withPropertyValues(invalidProperty)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fishbook.media.oss.endpoint=", "fishbook.media.oss.region=",
            "fishbook.media.oss.role-name="})
    void enabledOssRejectsOmittedConfiguration(String missingProperty) {
        contextRunner.withPropertyValues(Arrays.stream(VALID_OSS_PROPERTIES)
                        .filter(value -> !value.startsWith(missingProperty)).toArray(String[]::new))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void componentScanWithDisabledOssDoesNotCreateCloudResources() {
        new ApplicationContextRunner()
                .withInitializer(context -> new ClassPathBeanDefinitionScanner(
                        (BeanDefinitionRegistry) context.getBeanFactory(), true, context.getEnvironment())
                        .scan("com.fishbook.media.config"))
                .withPropertyValues("fishbook.media.enabled=false", "fishbook.media.provider=oss")
                .run(context -> {
                    assertThat(context).hasSingleBean(MediaStore.class);
                    assertThat(context.getBean(MediaStore.class)).isInstanceOf(DisabledMediaStore.class);
                    assertThat(context).doesNotHaveBean(OSS.class);
                    assertThat(context).doesNotHaveBean(OssRoleCredentialsProvider.class);
                    assertThat(context).doesNotHaveBean(OssProperties.class);
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                });
    }

    @Test
    void productionConfigurationOwnsShutdownAndClosesClientBeforeCredentials() {
        var closeAction = mock(Runnable.class);
        var provider = new OssRoleCredentialsProvider(() -> {
            throw new IllegalStateException("credentials must not be fetched");
        }, closeAction);
        var client = new AtomicReference<OSS>();
        try (var factory = mockStatic(OssRoleCredentialsProvider.class);
                var sdkClients = mockConstruction(OSSClient.class)) {
            factory.when(() -> OssRoleCredentialsProvider.forRole("test-role")).thenReturn(provider);
            contextRunner.withPropertyValues(VALID_OSS_PROPERTIES)
                    .run(context -> {
                        assertThat(context).hasSingleBean(OSS.class);
                        assertThat(sdkClients.constructed()).hasSize(1);
                        client.set(context.getBean(OSS.class));
                        assertThat(client.get()).isSameAs(sdkClients.constructed().getFirst());
                        verify(sdkClients.constructed().getFirst()).setRegion("cn-hangzhou");
                        assertThat(context).hasSingleBean(OssRoleCredentialsProvider.class);
                        assertThat(context.getBeanFactory().getBeanDefinition("ossClient")
                                .getDestroyMethodName()).isEqualTo("shutdown");
                        assertThat(context.getBeanFactory().getBeanDefinition("ossRoleCredentialsProvider")
                                .getDestroyMethodName()).isEqualTo("close");
                        verifyNoInteractions(closeAction);
                    });
            factory.verify(() -> OssRoleCredentialsProvider.forRole("test-role"));
        }
        var order = inOrder(client.get(), closeAction);
        order.verify(client.get()).shutdown();
        order.verify(closeAction).run();
        verify(client.get(), times(1)).shutdown();
        verify(closeAction, times(1)).run();
        verifyNoMoreInteractions(client.get(), closeAction);
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
