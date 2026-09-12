package com.fishbook.media.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.comm.ResponseMessage;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.fishbook.media.domain.MediaStorageUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.io.ContentLengthInputStream;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class OssMediaStoreTest {
    private final OSS client = mock(OSS.class);
    private final OssMediaStore store = new OssMediaStore(client, "test-bucket");

    @Test
    void uploadsBytesAndContentTypeWithoutPermissionOrBucketOperations() throws Exception {
        byte[] bytes = {1, 2, 3};
        store.put("catches/1/2/test", bytes, "image/png");
        var stream = ArgumentCaptor.forClass(InputStream.class);
        var metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(client).putObject(eq("test-bucket"), eq("catches/1/2/test"),
                stream.capture(), metadata.capture());
        assertThat(stream.getValue().readAllBytes()).isEqualTo(bytes);
        assertThat(metadata.getValue().getContentLength()).isEqualTo(3);
        assertThat(metadata.getValue().getContentType()).isEqualTo("image/png");
        verifyNoMoreInteractions(client);
    }

    @Test
    void readsContentTypeAndClosesResponse() throws Exception {
        var object = object(new ByteArrayInputStream(new byte[]{1, 2, 3}), "image/png");
        when(client.getObject("test-bucket", "catches/1/2/test")).thenReturn(object);
        var stored = store.get("catches/1/2/test");
        assertThat(stored.content()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(stored.contentType()).isEqualTo("image/png");
        verify(object).close();
        verify(client).getObject("test-bucket", "catches/1/2/test");
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NoSuchKey", "NoSuchObject"})
    void deletesMissingObjectIdempotently(String code) {
        doThrow(ossError(code)).when(client).deleteObject("test-bucket", "missing");
        assertThatCode(() -> store.delete("missing")).doesNotThrowAnyException();
        verify(client).deleteObject("test-bucket", "missing");
        verifyNoMoreInteractions(client);
    }

    @Test
    void deletesExistingObjectWithoutAdditionalOperations() {
        store.delete("present");
        verify(client).deleteObject("test-bucket", "present");
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @MethodSource("storageFailures")
    void deleteDoesNotSwallowDeniedOrTimeoutAndKeepsExternalMessageGeneric(RuntimeException failure) {
        doThrow(failure).when(client).deleteObject("test-bucket", "failed");
        assertThatThrownBy(() -> store.delete("failed"))
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
    }

    @ParameterizedTest
    @MethodSource("storageFailures")
    void putKeepsExternalMessageGeneric(RuntimeException failure) {
        doThrow(failure).when(client).putObject(eq("test-bucket"), eq("failed"),
                any(InputStream.class), any(ObjectMetadata.class));
        assertThatThrownBy(() -> store.put("failed", new byte[]{1}, "image/png"))
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
    }

    @ParameterizedTest
    @MethodSource("storageFailures")
    void getKeepsExternalMessageGeneric(RuntimeException failure) {
        when(client.getObject("test-bucket", "failed")).thenThrow(failure);
        assertThatThrownBy(() -> store.get("failed"))
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
    }

    @Test
    void acceptsExactlyTenMiBAndClosesResponseStream() throws Exception {
        byte[] bytes = new byte[10 * 1024 * 1024];
        var transport = new Transport(bytes.length, false, false);
        var object = httpObject(transport);
        when(client.getObject("test-bucket", "maximum")).thenReturn(object);
        assertThat(store.get("maximum").content()).isEqualTo(bytes);
        verify(object).close();
        assertThat(transport.readCount).isEqualTo(bytes.length);
        assertThat(transport.closed).isFalse(); // Fully consumed connections remain reusable.
        assertThatThrownBy(() -> object.getObjectContent().read()).isInstanceOf(IOException.class);
    }

    @Test
    void rejectsOversizeObjectWithoutReadingBeyondLimitAndClosesStream() throws Exception {
        var transport = new Transport(20 * 1024 * 1024, false, false);
        var object = httpObject(transport);
        when(client.getObject("test-bucket", "too-large")).thenReturn(object);
        assertThatThrownBy(() -> store.get("too-large"))
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
        // One extra byte is prefetched by the one-byte Apache session buffer.
        assertThat(transport.readCount).isEqualTo(10 * 1024 * 1024 + 2);
        assertThat(transport.closed).isTrue();
        verify(object).close();
        assertThatThrownBy(() -> object.getObjectContent().read()).isInstanceOf(IOException.class);
    }

    @Test
    void readFailureAbortsBeforeDrainingTheRemainingHttpEntity() throws Exception {
        var transport = new Transport(20 * 1024 * 1024, true, false);
        var object = httpObject(transport);
        when(client.getObject("test-bucket", "read-failed")).thenReturn(object);
        assertThatThrownBy(() -> store.get("read-failed"))
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
        assertThat(transport.readCount).isZero();
        assertThat(transport.closed).isTrue();
        assertThatThrownBy(() -> object.getObjectContent().read()).isInstanceOf(IOException.class);
    }

    @Test
    void abortFailureStillClosesEntityAndDoesNotExposeCleanupDetails() throws Exception {
        var transport = new Transport(20 * 1024 * 1024, false, true);
        var object = httpObject(transport);
        when(client.getObject("test-bucket", "abort-failed")).thenReturn(object);
        assertThatThrownBy(() -> store.get("abort-failed"))
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
        assertThat(transport.readCount).isEqualTo(10 * 1024 * 1024 + 2);
        assertThat(transport.closed).isTrue();
        assertThatThrownBy(() -> object.getObjectContent().read()).isInstanceOf(IOException.class);
    }

    @Test
    void readIOExceptionStillClosesResponseAndStream() throws Exception {
        var stream = spy(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("FAKE-sdk-error-body");
            }
        });
        var object = object(stream, "image/png");
        when(client.getObject("test-bucket", "unreadable")).thenReturn(object);
        assertThatThrownBy(() -> store.get("unreadable"))
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
        verify(object).close();
        verify(stream).close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingContentTypeAndClosesResponse(String contentType) throws Exception {
        var object = object(new ByteArrayInputStream(new byte[]{1}), contentType);
        when(client.getObject("test-bucket", "invalid-type")).thenReturn(object);
        assertThatThrownBy(() -> store.get("invalid-type"))
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
        verify(object).close();
    }

    @Test
    void responseCloseFailureUsesGenericExternalMessage() throws Exception {
        var object = object(new ByteArrayInputStream(new byte[]{1}), "image/png");
        doThrow(new IOException("FAKE-close-sdk-body")).when(object).close();
        when(client.getObject("test-bucket", "close-failed")).thenReturn(object);
        assertThatThrownBy(() -> store.get("close-failed"))
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasMessage("媒体存储暂时不可用");
    }

    private static Stream<RuntimeException> storageFailures() {
        return Stream.of(ossError("AccessDenied"), ossError("OtherFailure"),
                new ClientException("FAKE-timeout-sdk-error-body"));
    }

    private static OSSException ossError(String code) {
        return new OSSException("FAKE-sdk-error-body", code, "fake-request-id", "fake-host",
                "fake-resource", "fake-method", "fake-header");
    }

    private OSSObject object(InputStream stream, String contentType) {
        var object = spy(new OSSObject());
        var metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        object.setObjectMetadata(metadata);
        object.setObjectContent(stream);
        object.setResponse(new ResponseMessage(null));
        return object;
    }

    private OSSObject httpObject(Transport transport) {
        // Keep the real HTTP entity's draining close; fake only the socket boundary.
        var buffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 1);
        buffer.bind(transport);
        var object = object(new ContentLengthInputStream(buffer, transport.length), "image/png");
        object.getResponse().setHttpResponse(new TransportResponse(transport));
        return object;
    }

    private static final class TransportResponse extends BasicHttpResponse implements CloseableHttpResponse {
        private final Transport transport;

        TransportResponse(Transport transport) {
            super(HttpVersion.HTTP_1_1, 200, "OK");
            this.transport = transport;
        }

        @Override
        public void close() throws IOException {
            transport.close();
        }
    }

    private static final class Transport extends InputStream {
        private final int length;
        private final boolean failClose;
        private boolean failRead;
        private int readCount;
        private boolean closed;

        Transport(int length, boolean failRead, boolean failClose) {
            this.length = length;
            this.failRead = failRead;
            this.failClose = failClose;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0];
        }

        @Override
        public int read(byte[] bytes, int offset, int count) throws IOException {
            if (closed) throw new IOException("FAKE-closed-transport");
            if (failRead) {
                failRead = false; // A later draining close would resume downloading.
                throw new IOException("FAKE-read-secret");
            }
            if (readCount == length) return -1;
            int received = Math.min(count, length - readCount);
            java.util.Arrays.fill(bytes, offset, offset + received, (byte) 0);
            readCount += received;
            return received;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (failClose) throw new IOException("FAKE-abort-secret");
        }
    }
}
