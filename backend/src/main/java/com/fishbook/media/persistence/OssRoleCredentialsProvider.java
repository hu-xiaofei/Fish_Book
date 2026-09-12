package com.fishbook.media.persistence;

import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.credentials.provider.EcsRamRoleCredentialProvider;
import com.aliyun.oss.common.auth.Credentials;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentials;
import com.fishbook.media.domain.MediaStorageUnavailableException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class OssRoleCredentialsProvider implements CredentialsProvider, AutoCloseable {
    private final Supplier<CredentialModel> source;
    private final Runnable closeAction;
    private final AtomicBoolean closed = new AtomicBoolean();

    public OssRoleCredentialsProvider(Supplier<CredentialModel> source, Runnable closeAction) {
        this.source = source;
        this.closeAction = closeAction;
    }

    public static OssRoleCredentialsProvider forRole(String roleName) {
        var delegate = EcsRamRoleCredentialProvider.builder()
                .roleName(roleName).disableIMDSv1(true)
                .connectionTimeout(2000).readTimeout(3000)
                .asyncCredentialUpdateEnabled(false).build();
        return new OssRoleCredentialsProvider(delegate::getCredentials, delegate::close);
    }

    @Override
    public Credentials getCredentials() {
        try {
            var value = source.get();
            if (value == null || value.getAccessKeyId() == null || value.getAccessKeyId().isBlank()
                    || value.getAccessKeySecret() == null || value.getAccessKeySecret().isBlank()
                    || value.getSecurityToken() == null || value.getSecurityToken().isBlank()) {
                throw new MediaStorageUnavailableException();
            }
            return new DefaultCredentials(value.getAccessKeyId(), value.getAccessKeySecret(),
                    value.getSecurityToken());
        } catch (RuntimeException ex) {
            throw new MediaStorageUnavailableException(ex);
        }
    }

    @Override
    public void setCredentials(Credentials credentials) {
        throw new UnsupportedOperationException("Role credentials cannot be replaced");
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeAction.run();
        }
    }
}
