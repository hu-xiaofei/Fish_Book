package com.fishbook.media.cleanup;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record MediaCleanupJob(
        Long id,
        String objectKey,
        MediaCleanupReason reason,
        MediaCleanupStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        Instant createdAt) {

    private static final int MAX_ATTEMPTS = 8;

    public MediaCleanupJob {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("cleanup job ID must be positive");
        }
        if (objectKey == null || objectKey.isBlank() || objectKey.length() > 512) {
            throw new IllegalArgumentException("object key must contain 1 to 512 characters");
        }
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (attemptCount < 0 || attemptCount > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("attempt count must be between 0 and 8");
        }
        if (status == MediaCleanupStatus.PENDING
                && (attemptCount >= MAX_ATTEMPTS || nextAttemptAt == null)) {
            throw new IllegalArgumentException("pending jobs require a due time before attempt 8");
        }
        if (status == MediaCleanupStatus.FAILED
                && (attemptCount != MAX_ATTEMPTS || nextAttemptAt != null)) {
            throw new IllegalArgumentException("failed jobs must stop after attempt 8");
        }
    }

    public static MediaCleanupJob pending(
            String objectKey, MediaCleanupReason reason, Instant now) {
        return new MediaCleanupJob(
                null, objectKey, reason, MediaCleanupStatus.PENDING,
                0, now, null, now);
    }

    public MediaCleanupJob recordFailure(Instant attemptedAt) {
        Objects.requireNonNull(attemptedAt, "attemptedAt must not be null");
        var nextAttemptCount = attemptCount + 1;
        if (nextAttemptCount >= MAX_ATTEMPTS) {
            return new MediaCleanupJob(
                    id, objectKey, reason, MediaCleanupStatus.FAILED,
                    MAX_ATTEMPTS, null, attemptedAt, createdAt);
        }
        var delayMinutes = 1L << (nextAttemptCount - 1);
        return new MediaCleanupJob(
                id, objectKey, reason, MediaCleanupStatus.PENDING,
                nextAttemptCount,
                attemptedAt.plus(Duration.ofMinutes(delayMinutes)),
                attemptedAt,
                createdAt);
    }
}
