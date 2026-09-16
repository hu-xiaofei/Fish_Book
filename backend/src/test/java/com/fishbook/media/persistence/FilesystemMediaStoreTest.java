package com.fishbook.media.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.fishbook.media.domain.MediaStorageUnavailableException;
import com.fishbook.catchlog.application.CatchPhotoNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FilesystemMediaStoreTest {
    private static final String KEY = "catches/42/7/550e8400-e29b-41d4-a716-446655440000";
    private static final byte[] CONTENT = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
    @TempDir Path root;

    @Test
    void storesReadsAndDeletesOneObjectIdempotently() {
        var store = new FilesystemMediaStore(root);
        store.put(KEY, CONTENT, "image/jpeg");
        assertThat(store.get(KEY).content()).containsExactly(CONTENT);
        assertThat(store.get(KEY).contentType()).isEqualTo("image/jpeg");
        store.delete(KEY);
        store.delete(KEY);
        assertThat(root.resolve(KEY)).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    void preservesEachAllowedLiteralMime(String mime) {
        var store = new FilesystemMediaStore(root);
        store.put(KEY, CONTENT, mime);
        assertThat(store.get(KEY).contentType()).isEqualTo(mime);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "/tmp/x", "../x", "catches/../x", "./x", "x/./y",
            "catches\\x", "x/\\y", "x//y", "x/", "x/ /y", "x\u0000y"})
    void rejectsUnsafeKeysForEveryOperationWithoutDisclosingPaths(String key) {
        var store = new FilesystemMediaStore(root);
        unavailable(() -> store.put(key, CONTENT, "image/jpeg"), key);
        unavailable(() -> store.get(key), key);
        unavailable(() -> store.delete(key), key);
        assertThat(root).isEmptyDirectory();
    }

    @Test
    void rejectsSymbolicLinkParentWithoutTouchingOutsideFiles(@TempDir Path outside) throws Exception {
        Files.writeString(outside.resolve("sentinel-private-file"), "untouched");
        Files.createSymbolicLink(root.resolve("linked"), outside);
        var store = new FilesystemMediaStore(root);
        unavailable(() -> store.put("linked/object", CONTENT, "image/jpeg"), "linked/object");
        unavailable(() -> store.get("linked/object"), "linked/object");
        unavailable(() -> store.delete("linked/object"), "linked/object");
        assertThat(Files.readString(outside.resolve("sentinel-private-file"))).isEqualTo("untouched");
        assertThat(outside.resolve("object")).doesNotExist();
    }

    @Test
    void rejectsNonDirectoryParent() throws Exception {
        Files.writeString(root.resolve("parent"), "untouched");
        var store = new FilesystemMediaStore(root);
        unavailable(() -> store.put("parent/object", CONTENT, "image/jpeg"), "parent/object");
        unavailable(() -> store.get("parent/object"), "parent/object");
        unavailable(() -> store.delete("parent/object"), "parent/object");
        assertThat(Files.readString(root.resolve("parent"))).isEqualTo("untouched");
    }

    @Test
    void rejectsSecondPutAndPreservesOriginal() throws Exception {
        var store = new FilesystemMediaStore(root);
        store.put(KEY, CONTENT, "image/jpeg");
        unavailable(() -> store.put(KEY, new byte[] {7}, "image/png"), KEY);
        assertThat(store.get(KEY).content()).containsExactly(CONTENT);
        assertThat(store.get(KEY).contentType()).isEqualTo("image/jpeg");
        noTemporaryDirectories();
    }

    @Test
    void refusesToOverwriteEvenAnEmptyExistingObjectDirectory() throws Exception {
        Files.createDirectories(root.resolve(KEY));
        unavailable(() -> new FilesystemMediaStore(root).put(KEY, CONTENT, "image/jpeg"), KEY);
        assertThat(root.resolve(KEY)).isEmptyDirectory();
        noTemporaryDirectories();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"text/plain", "image/jpeg\n", "IMAGE/PNG", "image/png;charset=UTF-8"})
    void invalidMimeLeavesNoFinalOrTemporaryObject(String mime) throws Exception {
        unavailable(() -> new FilesystemMediaStore(root).put(KEY, CONTENT, mime), KEY);
        assertThat(root.resolve(KEY)).doesNotExist();
        noTemporaryDirectories();
    }

    @Test
    void rejectsNullAndOversizedBodiesWithoutPublishing() throws Exception {
        var store = new FilesystemMediaStore(root);
        unavailable(() -> store.put(KEY, null, "image/jpeg"), KEY);
        unavailable(() -> store.put(KEY, new byte[10 * 1024 * 1024 + 1], "image/jpeg"), KEY);
        assertThat(root.resolve(KEY)).doesNotExist();
        noTemporaryDirectories();
    }

    @Test
    void acceptsContentExactlyAtReadAndWriteLimit() {
        byte[] bytes = new byte[10 * 1024 * 1024];
        bytes[bytes.length - 1] = 9;
        var store = new FilesystemMediaStore(root);
        store.put(KEY, bytes, "image/png");
        assertThat(store.get(KEY).content()).containsExactly(bytes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "text/plain", "image/jpeg\n", "image/png; charset=utf-8"})
    void rejectsMalformedMimeMetadata(String mime) throws Exception {
        fixture();
        Files.writeString(root.resolve(KEY).resolve("content-type"), mime);
        unavailable(() -> new FilesystemMediaStore(root).get(KEY), KEY);
    }

    @Test
    void rejectsOversizedMimeMetadata() throws Exception {
        fixture();
        Files.writeString(root.resolve(KEY).resolve("content-type"), "image/jpeg" + " ".repeat(100_000));
        unavailable(() -> new FilesystemMediaStore(root).get(KEY), KEY);
    }

    @Test
    void rejectsOversizedStoredContent() throws Exception {
        fixture();
        Files.write(root.resolve(KEY).resolve("content"), new byte[10 * 1024 * 1024 + 1]);
        unavailable(() -> new FilesystemMediaStore(root).get(KEY), KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"content", "content-type", "object"})
    void missingObjectOrFixedChildUsesPhotoNotFoundSemantics(String missing) throws Exception {
        if (!missing.equals("object")) {
            fixture();
            Files.delete(root.resolve(KEY).resolve(missing));
        }
        assertThatThrownBy(() -> new FilesystemMediaStore(root).get(KEY))
                .isInstanceOf(CatchPhotoNotFoundException.class)
                .hasMessageNotContaining(root.toString()).hasMessageNotContaining(KEY).hasNoCause();
        new FilesystemMediaStore(root).delete(KEY);
        assertThat(root.resolve(KEY)).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"content", "content-type"})
    void rejectsSymbolicLinkChildrenOnReadAndDelete(String child, @TempDir Path outside) throws Exception {
        fixture();
        Path sentinel = Files.writeString(outside.resolve("sentinel-private-file"), "image/jpeg");
        Files.delete(root.resolve(KEY).resolve(child));
        Files.createSymbolicLink(root.resolve(KEY).resolve(child), sentinel);
        var store = new FilesystemMediaStore(root);
        unavailable(() -> store.get(KEY), KEY);
        unavailable(() -> store.delete(KEY), KEY);
        assertThat(Files.readString(sentinel)).isEqualTo("image/jpeg");
        assertThat(Files.isSymbolicLink(root.resolve(KEY).resolve(child))).isTrue();
    }

    @Test
    void rejectsSymbolicLinkObjectDirectory(@TempDir Path outside) throws Exception {
        Files.write(outside.resolve("content"), CONTENT);
        Files.writeString(outside.resolve("content-type"), "image/jpeg");
        Files.createDirectories(root.resolve(KEY).getParent());
        Files.createSymbolicLink(root.resolve(KEY), outside);
        var store = new FilesystemMediaStore(root);
        unavailable(() -> store.get(KEY), KEY);
        unavailable(() -> store.delete(KEY), KEY);
        unavailable(() -> store.put(KEY, CONTENT, "image/jpeg"), KEY);
        assertThat(Files.readAllBytes(outside.resolve("content"))).containsExactly(CONTENT);
    }

    @Test
    void deleteFailsClosedForUnexpectedEntryWithoutRemovingAnyFiles() throws Exception {
        fixture();
        Files.writeString(root.resolve(KEY).resolve("sentinel-private-file"), "untouched");
        unavailable(() -> new FilesystemMediaStore(root).delete(KEY), KEY);
        assertThat(Files.readAllBytes(root.resolve(KEY).resolve("content"))).containsExactly(CONTENT);
        assertThat(Files.readString(root.resolve(KEY).resolve("sentinel-private-file"))).isEqualTo("untouched");
    }

    @ParameterizedTest
    @ValueSource(strings = {"content", "content-type"})
    void deleteRejectsDirectoriesInPlaceOfFixedFiles(String child) throws Exception {
        fixture();
        Files.delete(root.resolve(KEY).resolve(child));
        Files.createDirectory(root.resolve(KEY).resolve(child));
        var store = new FilesystemMediaStore(root);
        unavailable(() -> store.get(KEY), KEY);
        unavailable(() -> store.delete(KEY), KEY);
        assertThat(root.resolve(KEY).resolve(child)).isDirectory();
    }

    @Test
    void cleansTemporaryFilesWhenFinalPublicationFails() throws Exception {
        var store = new FilesystemMediaStore(root);
        // Reject only the publication operation after both real files have been fully written.
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.move(any(Path.class), any(Path.class), eq(StandardCopyOption.ATOMIC_MOVE)))
                    .thenAnswer(invocation -> {
                        Path staged = invocation.getArgument(0);
                        assertThat(Files.readAllBytes(staged.resolve("content"))).containsExactly(CONTENT);
                        assertThat(Files.readString(staged.resolve("content-type"))).isEqualTo("image/jpeg");
                        throw new IOException("synthetic-publication-failure");
                    });
            unavailable(() -> store.put(KEY, CONTENT, "image/jpeg"), KEY);
        }
        noTemporaryDirectories();
        assertThat(root.resolve(KEY)).doesNotExist();
    }

    @Test
    void objectRemainsInvisibleUntilBothFilesAreWritten() throws Exception {
        var store = new FilesystemMediaStore(root);
        var observedBeforeMetadata = new AtomicBoolean();
        // Observe the exact intermediate state; filesystem watchers may coalesce short-lived entries.
        // Every filesystem operation still runs against the real temporary directory.
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.writeString(any(Path.class), any(CharSequence.class),
                    eq(StandardCharsets.UTF_8), eq(StandardOpenOption.CREATE_NEW))).thenAnswer(invocation -> {
                        observedBeforeMetadata.set(true);
                        assertThat(root.resolve(KEY)).doesNotExist();
                        assertThatThrownBy(() -> store.get(KEY)).isInstanceOf(CatchPhotoNotFoundException.class);
                        return invocation.callRealMethod();
                    });
            store.put(KEY, CONTENT, "image/jpeg");
        }
        assertThat(observedBeforeMetadata).isTrue();
        assertThat(store.get(KEY).content()).containsExactly(CONTENT);
        assertThat(store.get(KEY).contentType()).isEqualTo("image/jpeg");
        noTemporaryDirectories();
    }

    @ParameterizedTest
    @ValueSource(strings = {"put", "delete"})
    void serializesOverlappingMutationsAcrossStoreInstances(String operation) throws Exception {
        var firstStore = new FilesystemMediaStore(root);
        var secondStore = new FilesystemMediaStore(root.toRealPath());
        var publicationPending = new CountDownLatch(1);
        var releasePublication = new CountDownLatch(1);
        var firstResult = new FutureTask<Void>(() -> {
            try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                files.when(() -> Files.move(any(Path.class), any(Path.class), eq(StandardCopyOption.ATOMIC_MOVE)))
                        .thenAnswer(invocation -> {
                            publicationPending.countDown();
                            if (!releasePublication.await(10, TimeUnit.SECONDS))
                                throw new AssertionError("Publication was not released");
                            return invocation.callRealMethod();
                        });
                firstStore.put(KEY, CONTENT, "image/jpeg");
            }
            return null;
        });
        var secondResult = new FutureTask<RuntimeException>(() -> {
            try {
                if (operation.equals("put")) secondStore.put(KEY, new byte[] {7}, "image/png");
                else secondStore.delete(KEY);
                return null;
            } catch (RuntimeException failure) {
                return failure;
            }
        });
        Thread first = new Thread(firstResult, "first-media-writer");
        Thread second = new Thread(secondResult, "second-media-writer");
        first.start();
        try {
            assertThat(publicationPending.await(5, TimeUnit.SECONDS)).isTrue();
            second.start();
            awaitBlockedByOrFinished(second, first);
            assertThat(secondResult.isDone()).as("the second mutation must wait for publication").isFalse();
        } finally {
            releasePublication.countDown();
            first.join(5_000);
            second.join(5_000);
        }
        firstResult.get(5, TimeUnit.SECONDS);
        if (operation.equals("put")) {
            assertThat(secondResult.get(5, TimeUnit.SECONDS)).isInstanceOf(MediaStorageUnavailableException.class);
            assertThat(firstStore.get(KEY).content()).containsExactly(CONTENT);
            assertThat(firstStore.get(KEY).contentType()).isEqualTo("image/jpeg");
        } else {
            assertThat(secondResult.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(root.resolve(KEY)).doesNotExist();
        }
        noTemporaryDirectories();
    }

    @Test
    void ioFailureStackTraceCannotRevealRawPathsOrKeys() throws Exception {
        var store = new FilesystemMediaStore(root);
        var raw = new IOException(root + "/sentinel-private-file " + KEY);
        raw.addSuppressed(new IOException("sentinel-private-file"));
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.writeString(any(Path.class), any(CharSequence.class),
                    eq(StandardCharsets.UTF_8), eq(StandardOpenOption.CREATE_NEW))).thenThrow(raw);
            assertThatThrownBy(() -> store.put(KEY, CONTENT, "image/jpeg"))
                    .isInstanceOf(MediaStorageUnavailableException.class).hasNoCause()
                    .satisfies(failure -> {
                        var trace = new StringWriter();
                        failure.printStackTrace(new PrintWriter(trace));
                        assertThat(trace.toString()).doesNotContain(root.toString(), KEY, "sentinel-private-file");
                    });
        }
        noTemporaryDirectories();
    }

    @Test
    void cleansWrittenContentWhenMetadataWriteFails() throws Exception {
        var store = new FilesystemMediaStore(root);
        // Only this I/O failure is injected; body creation and failure cleanup use real files.
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.writeString(any(Path.class), any(CharSequence.class),
                    eq(StandardCharsets.UTF_8), eq(StandardOpenOption.CREATE_NEW)))
                    .thenThrow(new IOException("synthetic-write-failure"));
            unavailable(() -> store.put(KEY, CONTENT, "image/jpeg"), KEY);
        }
        assertThat(root.resolve(KEY)).doesNotExist();
        noTemporaryDirectories();
    }

    @Test
    void validatesRootAndRejectsRootReplacedWithSymlink(@TempDir Path outside) throws Exception {
        unavailable(() -> new FilesystemMediaStore(Path.of("relative")), "relative");
        unavailable(() -> new FilesystemMediaStore(root.resolve("missing")), "missing");
        Path configured = Files.createDirectory(root.resolve("configured"));
        var store = new FilesystemMediaStore(configured);
        Files.delete(configured);
        Files.createSymbolicLink(configured, outside);
        unavailable(() -> store.put(KEY, CONTENT, "image/jpeg"), KEY);
        assertThat(outside).isEmptyDirectory();
    }

    private void fixture() throws Exception {
        Files.createDirectories(root.resolve(KEY));
        Files.write(root.resolve(KEY).resolve("content"), CONTENT);
        Files.writeString(root.resolve(KEY).resolve("content-type"), "image/jpeg");
    }

    private void unavailable(ThrowingCallable action, String key) {
        var assertion = assertThatThrownBy(action).isInstanceOf(MediaStorageUnavailableException.class)
                .hasNoCause().hasMessageNotContaining(root.toString()).hasMessageNotContaining("sentinel-private-file");
        if (key != null && !key.isEmpty()) assertion.hasMessageNotContaining(key);
    }

    private static void awaitBlockedByOrFinished(Thread waiter, Thread owner) throws Exception {
        var threads = ManagementFactory.getThreadMXBean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (waiter.isAlive() && System.nanoTime() < deadline) {
            var info = threads.getThreadInfo(waiter.threadId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockOwnerId() == owner.threadId()) return;
            Thread.sleep(1);
        }
        assertThat(waiter.isAlive()).as("writer must either finish or reach the active writer's lock").isFalse();
    }

    private void noTemporaryDirectories() throws Exception {
        try (var paths = Files.walk(root)) {
            assertThat(paths.filter(path -> path.getFileName().toString().startsWith(".upload-"))).isEmpty();
        }
    }
}
