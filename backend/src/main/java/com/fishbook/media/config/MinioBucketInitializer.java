package com.fishbook.media.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fishbook.media.enabled", havingValue = "true")
@ConditionalOnProperty(name = "fishbook.media.provider", havingValue = "minio", matchIfMissing = true)
public class MinioBucketInitializer implements ApplicationRunner {
    private final MinioClient minioClient;
    private final MediaProperties properties;

    public MinioBucketInitializer(MinioClient minioClient, MediaProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            var bucketExists = BucketExistsArgs.builder().bucket(properties.bucket()).build();
            if (!minioClient.bucketExists(bucketExists)) {
                var makeBucket = MakeBucketArgs.builder().bucket(properties.bucket()).build();
                minioClient.makeBucket(makeBucket);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize private media bucket", exception);
        }
    }
}
