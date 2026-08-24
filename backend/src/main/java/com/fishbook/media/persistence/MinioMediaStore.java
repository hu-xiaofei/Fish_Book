package com.fishbook.media.persistence;

import com.fishbook.media.config.MediaProperties;
import com.fishbook.media.domain.MediaStorageUnavailableException;
import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.domain.StoredMedia;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;

public final class MinioMediaStore implements MediaStore {
    private final MinioClient minioClient;
    private final MediaProperties properties;

    public MinioMediaStore(MinioClient minioClient, MediaProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        try (var stream = new ByteArrayInputStream(content)) {
            var request = PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(stream, (long) content.length, -1L)
                    .contentType(contentType)
                    .build();
            minioClient.putObject(request);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public StoredMedia get(String objectKey) {
        try {
            var statRequest = StatObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build();
            var stat = minioClient.statObject(statRequest);
            var getRequest = GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build();
            try (var response = minioClient.getObject(getRequest)) {
                return new StoredMedia(response.readAllBytes(), stat.contentType());
            }
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            var request = RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build();
            minioClient.removeObject(request);
        } catch (ErrorResponseException exception) {
            var code = exception.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                return;
            }
            throw unavailable(exception);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private static MediaStorageUnavailableException unavailable(Exception cause) {
        return new MediaStorageUnavailableException(cause);
    }
}
