package com.fishbook.media.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.oss.common.auth.DefaultCredentials;
import com.fishbook.media.domain.MediaStorageUnavailableException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OssRoleCredentialsProviderTest {
    @Test
    void forwardsAllFieldsFromEachRotatingSessionCredential() {
        var next = new AtomicInteger();
        var provider = new OssRoleCredentialsProvider(() -> {
            int generation = next.incrementAndGet();
            return CredentialModel.builder()
                    .accessKeyId("fake-id-" + generation)
                    .accessKeySecret("fake-secret-" + generation)
                    .securityToken("fake-token-" + generation).build();
        }, () -> {});

        var first = provider.getCredentials();
        assertThat(first).isNotNull();
        assertThat(first.getAccessKeyId()).isEqualTo("fake-id-1");
        assertThat(first.getSecretAccessKey()).isEqualTo("fake-secret-1");
        assertThat(first.getSecurityToken()).isEqualTo("fake-token-1");
        var refreshed = provider.getCredentials();
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.getAccessKeyId()).isEqualTo("fake-id-2");
        assertThat(refreshed.getSecretAccessKey()).isEqualTo("fake-secret-2");
        assertThat(refreshed.getSecurityToken()).isEqualTo("fake-token-2");
    }

    @Test
    void constructionDoesNotRequestCredentials() {
        var requests = new AtomicInteger();
        new OssRoleCredentialsProvider(() -> {
            requests.incrementAndGet();
            return CredentialModel.builder().build();
        }, () -> {});

        assertThat(requests.get()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void missingTokenCannotBecomeLongLivedCredentials(String token) {
        var provider = new OssRoleCredentialsProvider(() -> CredentialModel.builder()
                .accessKeyId("fake-id").accessKeySecret("fake-secret")
                .securityToken(token).build(), () -> {});

        assertThatThrownBy(provider::getCredentials)
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingAccessKeyId(String accessKeyId) {
        var provider = new OssRoleCredentialsProvider(() -> CredentialModel.builder()
                .accessKeyId(accessKeyId).accessKeySecret("fake-secret")
                .securityToken("fake-token").build(), () -> {});

        assertThatThrownBy(provider::getCredentials)
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingAccessKeySecret(String secret) {
        var provider = new OssRoleCredentialsProvider(() -> CredentialModel.builder()
                .accessKeyId("fake-id").accessKeySecret(secret)
                .securityToken("fake-token").build(), () -> {});

        assertThatThrownBy(provider::getCredentials)
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
    }

    @Test
    void rejectsNullCredentialModel() {
        var provider = new OssRoleCredentialsProvider(() -> null, () -> {});

        assertThatThrownBy(provider::getCredentials)
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
    }

    @Test
    void sourceFailureHasOnlyGenericTopLevelMessage() {
        var provider = new OssRoleCredentialsProvider(() -> {
            throw new IllegalStateException("fake-sensitive-sdk-body");
        }, () -> {});

        assertThatThrownBy(provider::getCredentials)
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
    }

    @Test
    void manualCredentialsCannotReplaceRoleCredentials() {
        var provider = new OssRoleCredentialsProvider(() -> CredentialModel.builder()
                .accessKeyId("fake-role-id").accessKeySecret("fake-role-secret")
                .securityToken("fake-role-token").build(), () -> {});

        assertThatThrownBy(() -> provider.setCredentials(
                new DefaultCredentials("fake-manual-id", "fake-manual-secret")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(provider.getCredentials().getAccessKeyId()).isEqualTo("fake-role-id");
    }

    @Test
    void closesOwnedCredentialSourceAtMostOnce() {
        var closes = new AtomicInteger();
        var provider = new OssRoleCredentialsProvider(() -> null, closes::incrementAndGet);

        provider.close();
        provider.close();

        assertThat(closes.get()).isEqualTo(1);
    }

    @Test
    void failingCloseIsNotRetried() {
        var closes = new AtomicInteger();
        var provider = new OssRoleCredentialsProvider(() -> null, () -> {
            closes.incrementAndGet();
            throw new IllegalStateException("fake-close-failure");
        });

        assertThatThrownBy(provider::close).isInstanceOf(IllegalStateException.class);
        provider.close();

        assertThat(closes.get()).isEqualTo(1);
    }
}
