package com.fishbook.media.config;

import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.persistence.DisabledMediaStore;
import com.fishbook.media.persistence.MinioMediaStore;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaProperties.class)
public class MinioConfiguration {
    @Bean
    @ConditionalOnProperty(name = "fishbook.media.enabled", havingValue = "true")
    MinioClient minioClient(MediaProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "fishbook.media.enabled", havingValue = "true")
    MediaStore minioMediaStore(MinioClient minioClient, MediaProperties properties) {
        return new MinioMediaStore(minioClient, properties);
    }

    @Bean
    @ConditionalOnProperty(
            name = "fishbook.media.enabled",
            havingValue = "false",
            matchIfMissing = true)
    MediaStore disabledMediaStore() {
        return new DisabledMediaStore();
    }
}
