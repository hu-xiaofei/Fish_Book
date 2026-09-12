package com.fishbook.media.persistence;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.fishbook.media.domain.MediaStorageUnavailableException;
import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.domain.StoredMedia;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class OssMediaStore implements MediaStore {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final OSS client;
    private final String bucket;

    public OssMediaStore(OSS client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        try {
            var metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            metadata.setContentType(contentType);
            client.putObject(bucket, objectKey, new ByteArrayInputStream(content), metadata);
        } catch (RuntimeException ex) {
            throw new MediaStorageUnavailableException(ex);
        }
    }

    @Override
    public StoredMedia get(String objectKey) {
        try (var object = client.getObject(bucket, objectKey)) {
            byte[] bytes;
            boolean consumed = false;
            try {
                bytes = object.getObjectContent().readNBytes(MAX_BYTES + 1);
                consumed = bytes.length <= MAX_BYTES;
            } finally {
                // Normal HTTP entity close drains unread bytes to reuse the connection.
                // Abort incomplete responses first, then let try-with-resources close the stream.
                if (!consumed) {
                    object.forcedClose();
                }
            }
            String type = object.getObjectMetadata().getContentType();
            if (bytes.length > MAX_BYTES || type == null || type.isBlank()) {
                throw new MediaStorageUnavailableException();
            }
            return new StoredMedia(bytes, type);
        } catch (IOException | RuntimeException ex) {
            throw new MediaStorageUnavailableException(ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(bucket, objectKey);
        } catch (OSSException ex) {
            if (!"NoSuchKey".equals(ex.getErrorCode()) && !"NoSuchObject".equals(ex.getErrorCode())) {
                throw new MediaStorageUnavailableException(ex);
            }
        } catch (RuntimeException ex) {
            throw new MediaStorageUnavailableException(ex);
        }
    }
}
