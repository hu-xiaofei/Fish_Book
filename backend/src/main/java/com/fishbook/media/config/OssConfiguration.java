package com.fishbook.media.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.comm.SignVersion;
import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.persistence.OssMediaStore;
import com.fishbook.media.persistence.OssRoleCredentialsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "fishbook.media.enabled", havingValue = "true")
@ConditionalOnProperty(name = "fishbook.media.provider", havingValue = "oss")
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(OssRoleCredentialsProvider.class)
    OssRoleCredentialsProvider ossRoleCredentialsProvider(OssProperties properties) {
        return OssRoleCredentialsProvider.forRole(properties.roleName());
    }

    @Bean(destroyMethod = "shutdown")
    OSS ossClient(OssProperties properties, OssRoleCredentialsProvider credentials) {
        var configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        configuration.setConnectionTimeout(3000);
        configuration.setSocketTimeout(10000);
        configuration.setMaxErrorRetry(2);
        configuration.setVerifySSLEnable(true);
        return OSSClientBuilder.create().endpoint(properties.endpoint()).region(properties.region())
                .credentialsProvider(credentials).clientConfiguration(configuration).build();
    }

    @Bean
    MediaStore ossMediaStore(OSS client, MediaProperties properties) {
        return new OssMediaStore(client, properties.bucket());
    }
}
