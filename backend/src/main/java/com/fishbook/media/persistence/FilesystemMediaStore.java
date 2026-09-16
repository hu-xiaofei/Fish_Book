package com.fishbook.media.persistence;

import com.fishbook.catchlog.application.CatchPhotoNotFoundException;
import com.fishbook.media.domain.MediaStorageUnavailableException;
import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.domain.StoredMedia;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.Objects;

/**
 * Private storage for one backend JVM. The protected directory must have no independent
 * process or hostile local writer. All application mutations must use this class: JVM-wide
 * locks coordinate its instances, while ATOMIC_MOVE publishes complete objects. Java does
 * not offer portable atomic no-replace directory moves against an external writer.
 */
public final class FilesystemMediaStore implements MediaStore {
    static final long MAX_CONTENT_BYTES = 10L * 1024 * 1024;
    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String CONTENT = "content";
    private static final String CONTENT_TYPE = "content-type";
    private static final int MAX_CONTENT_TYPE_BYTES = 10;
    // Bounded stripes avoid retaining one lock forever for every uploaded object key.
    private static final Object[] MUTATION_LOCKS = new Object[256];
    static {
        Arrays.setAll(MUTATION_LOCKS, ignored -> new Object());
    }
    private final Path root;

    public FilesystemMediaStore(Path root) {
        try {
            if (root == null || !root.isAbsolute()) throw new IOException("Invalid media root");
            Path normalized = root.normalize();
            requireDirectory(normalized);
            this.root = normalized.toRealPath();
            requireDirectory(this.root);
            if (!Files.isReadable(this.root) || !Files.isWritable(this.root))
                throw new IOException("Media root is unavailable");
        } catch (IOException | RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        synchronized (mutationLock(objectKey)) {
            putLocked(objectKey, content, contentType);
        }
    }

    private void putLocked(String objectKey, byte[] content, String contentType) {
        Path temporary = null;
        try {
            if (content == null || content.length > MAX_CONTENT_BYTES
                    || contentType == null || !CONTENT_TYPES.contains(contentType))
                throw new IOException("Invalid media content");
            Path target = resolveObject(objectKey);
            createParents(target.getParent());
            requireAbsent(target);
            Path staging = target.resolveSibling(".upload-" + UUID.randomUUID());
            Files.createDirectory(staging);
            temporary = staging;
            Files.write(staging.resolve(CONTENT), content, StandardOpenOption.CREATE_NEW);
            Files.writeString(staging.resolve(CONTENT_TYPE), contentType,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            // All cooperating put/delete calls hold the same lock through this check and move.
            resolveObject(objectKey);
            requireAbsent(target);
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            temporary = null;
        } catch (IOException | RuntimeException failure) {
            if (temporary != null) cleanupTemporary(temporary, failure);
            throw unavailable(failure);
        }
    }

    @Override
    public StoredMedia get(String objectKey) {
        try {
            Path target = resolveObject(objectKey);
            requireDirectory(target);
            Path content = target.resolve(CONTENT);
            Path metadata = target.resolve(CONTENT_TYPE);
            requireRegularFile(content);
            requireRegularFile(metadata);
            if (Files.size(content) > MAX_CONTENT_BYTES || Files.size(metadata) > MAX_CONTENT_TYPE_BYTES)
                throw new IOException("Invalid media size");
            byte[] bytes;
            String contentType;
            try (var input = Files.newInputStream(content, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes((int) MAX_CONTENT_BYTES + 1);
            }
            try (var input = Files.newInputStream(metadata, LinkOption.NOFOLLOW_LINKS)) {
                contentType = new String(input.readNBytes(MAX_CONTENT_TYPE_BYTES + 1), StandardCharsets.UTF_8);
            }
            if (bytes.length > MAX_CONTENT_BYTES || !CONTENT_TYPES.contains(contentType))
                throw new IOException("Invalid stored media");
            return new StoredMedia(bytes, contentType);
        } catch (NoSuchFileException missing) {
            throw new CatchPhotoNotFoundException();
        } catch (IOException | RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public void delete(String objectKey) {
        synchronized (mutationLock(objectKey)) {
            deleteLocked(objectKey);
        }
    }

    private void deleteLocked(String objectKey) {
        try {
            Path target = resolveObject(objectKey);
            if (attributesIfPresent(target) == null) return;
            requireDirectory(target);
            // Validate the complete object before deleting anything, including corrupt entries.
            try (var entries = Files.newDirectoryStream(target)) {
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    if (!name.equals(CONTENT) && !name.equals(CONTENT_TYPE))
                        throw new IOException("Unexpected media entry");
                    requireRegularFile(entry);
                }
            }
            deleteRegularFileIfPresent(target.resolve(CONTENT));
            deleteRegularFileIfPresent(target.resolve(CONTENT_TYPE));
            Files.deleteIfExists(target);
        } catch (IOException | RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    private Object mutationLock(String objectKey) {
        return MUTATION_LOCKS[Math.floorMod(Objects.hash(root, objectKey), MUTATION_LOCKS.length)];
    }

    private Path resolveObject(String objectKey) throws IOException {
        if (objectKey == null || objectKey.isBlank()) throw new IOException("Invalid media key");
        Path candidate = root;
        for (String component : objectKey.split("/", -1)) {
            if (component.isBlank() || component.equals(".") || component.equals("..")
                    || component.indexOf('\\') >= 0)
                throw new IOException("Invalid media key");
            candidate = candidate.resolve(component);
        }
        candidate = candidate.normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) throw new IOException("Invalid media key");
        requireDirectory(root);
        Path parent = root;
        for (Path component : root.relativize(candidate.getParent())) {
            parent = parent.resolve(component);
            var attributes = attributesIfPresent(parent);
            if (attributes != null && !attributes.isDirectory()) throw new IOException("Invalid media parent");
        }
        return candidate;
    }

    private void createParents(Path parent) throws IOException {
        Path current = root;
        for (Path component : root.relativize(parent)) {
            current = current.resolve(component);
            try {
                Files.createDirectory(current);
            } catch (FileAlreadyExistsException exists) {
                // Another upload may have created a shared parent; validate it without following links.
            }
            requireDirectory(current);
        }
    }

    private static void requireAbsent(Path path) throws IOException {
        if (attributesIfPresent(path) != null) throw new IOException("Media object already exists");
    }

    private static BasicFileAttributes attributesIfPresent(Path path) throws IOException {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException missing) {
            return null;
        }
    }

    private static void requireDirectory(Path path) throws IOException {
        if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory())
            throw new IOException("Invalid media directory");
    }

    private static void requireRegularFile(Path path) throws IOException {
        if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile())
            throw new IOException("Invalid media file");
    }

    private static void deleteRegularFileIfPresent(Path path) throws IOException {
        var attributes = attributesIfPresent(path);
        if (attributes == null) return;
        if (!attributes.isRegularFile()) throw new IOException("Invalid media file");
        Files.deleteIfExists(path);
    }

    private static void cleanupTemporary(Path temporary, Throwable failure) {
        for (Path path : new Path[] {temporary.resolve(CONTENT), temporary.resolve(CONTENT_TYPE), temporary}) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException | RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static MediaStorageUnavailableException unavailable(Throwable cause) {
        // Even a logger printing the whole exception must not reveal paths from I/O causes.
        return new MediaStorageUnavailableException();
    }
}
