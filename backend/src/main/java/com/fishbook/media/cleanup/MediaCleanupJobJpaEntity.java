package com.fishbook.media.cleanup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "media_cleanup_jobs")
class MediaCleanupJobJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MediaCleanupReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaCleanupStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MediaCleanupJobJpaEntity() {}

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getObjectKey() {
        return objectKey;
    }

    void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    MediaCleanupReason getReason() {
        return reason;
    }

    void setReason(MediaCleanupReason reason) {
        this.reason = reason;
    }

    MediaCleanupStatus getStatus() {
        return status;
    }

    void setStatus(MediaCleanupStatus status) {
        this.status = status;
    }

    int getAttemptCount() {
        return attemptCount;
    }

    void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
