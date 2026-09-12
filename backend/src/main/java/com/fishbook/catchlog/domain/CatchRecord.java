package com.fishbook.catchlog.domain;

import java.time.Instant;
import java.util.Objects;

public record CatchRecord(
        Long id,
        long userId,
        CatchRecordDetails details,
        String photoObjectKey,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public CatchRecord {
        Objects.requireNonNull(details, "details must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (userId <= 0 || (id != null && id <= 0)) {
            throw new IllegalArgumentException("record and user IDs must be positive");
        }
        if (photoObjectKey != null
                && (photoObjectKey.isBlank() || photoObjectKey.length() > 512)) {
            throw new IllegalArgumentException("photo object key must contain 1 to 512 characters");
        }
    }

    /** Compatibility constructor for local fixtures; persistence must supply the stored version. */
    public CatchRecord(Long id, long userId, CatchRecordDetails details, String photoObjectKey,
            Instant createdAt, Instant updatedAt) {
        this(id, userId, details, photoObjectKey, createdAt, updatedAt, id == null ? null : 0L);
    }

    public static CatchRecord create(long userId, CatchRecordDetails details, Instant now) {
        return new CatchRecord(null, userId, details, null, now, now, null);
    }

    public static CatchRecord restore(
            long id, long userId, CatchRecordDetails details, String photoObjectKey,
            Instant createdAt, Instant updatedAt) {
        return new CatchRecord(id, userId, details, photoObjectKey, createdAt, updatedAt);
    }

    public static CatchRecord restore(
            long id, long userId, CatchRecordDetails details, String photoObjectKey,
            Instant createdAt, Instant updatedAt, Long version) {
        return new CatchRecord(id, userId, details, photoObjectKey, createdAt, updatedAt, version);
    }

    public CatchRecord update(CatchRecordDetails next, Instant now) {
        return new CatchRecord(id, userId, next, photoObjectKey, createdAt, now, version);
    }

    public CatchRecord withPhotoObjectKey(String objectKey, Instant now) {
        return new CatchRecord(id, userId, details, objectKey, createdAt, now, version);
    }

    public CatchRecord withoutPhoto(Instant now) {
        return new CatchRecord(id, userId, details, null, createdAt, now, version);
    }
}
