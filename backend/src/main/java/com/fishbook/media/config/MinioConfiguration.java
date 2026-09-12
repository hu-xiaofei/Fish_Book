package com.fishbook.media.config;

import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.persistence.MinioMediaStore;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "fishbook.media.enabled", havingValue = "true")
@ConditionalOnProperty(name = "fishbook.media.provider", havingValue = "minio", matchIfMissing = true)
public class MinioConfiguration {
    @Bean
    MinioClient minioClient(MediaProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    MediaStore minioMediaStore(MinioClient minioClient, MediaProperties properties) {
        return new MinioMediaStore(minioClient, properties);
    }
}
