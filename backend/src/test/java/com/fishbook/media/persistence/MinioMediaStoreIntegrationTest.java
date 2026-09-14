package com.fishbook.media.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fishbook.media.config.MediaProperties;
import com.fishbook.media.domain.MediaStorageUnavailableException;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class MinioMediaStoreIntegrationTest {
    private static final String IMAGE = "quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1";
    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key-123";
    private static final String BUCKET = "catch-photo-test";

    @Test
    void storesReadsDeletesIdempotentlyAndMapsUnavailableStorage() throws Exception {
        var container = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
                .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
                .withCommand("server", "/data")
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

        try {
            container.start();
            var endpoint = "http://" + container.getHost() + ":" + container.getMappedPort(9000);
            var properties = new MediaProperties(
                    true, endpoint, ACCESS_KEY, SECRET_KEY, BUCKET);
            var client = MinioClient.builder()
                    .endpoint(properties.endpoint())
                    .credentials(properties.accessKey(), properties.secretKey())
                    .build();
            client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
            var store = new MinioMediaStore(client, properties);
            var objectKey = "catches/42/7/test-object";
            var content = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};

            store.put(objectKey, content, "image/jpeg");

            var stored = store.get(objectKey);
            assertThat(stored.content()).containsExactly(content);
            assertThat(stored.contentType()).isEqualTo("image/jpeg");

            store.delete(objectKey);
            store.delete(objectKey);

            container.stop();
            assertThatThrownBy(() -> store.get(objectKey))
                    .isInstanceOf(MediaStorageUnavailableException.class)
                    .hasMessage("媒体存储暂时不可用")
                    .hasMessageNotContaining(endpoint)
                    .hasMessageNotContaining(ACCESS_KEY)
                    .hasMessageNotContaining(SECRET_KEY);
        } finally {
            container.stop();
        }
    }
}
