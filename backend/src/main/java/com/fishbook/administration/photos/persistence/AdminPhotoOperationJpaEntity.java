package com.fishbook.administration.photos.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_photo_operations")
class AdminPhotoOperationJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_user_id", nullable = false)
    private long actorUserId;
    @Column(name = "owner_user_id", nullable = false)
    private long ownerUserId;
    @Column(name = "catch_record_id", nullable = false)
    private long recordId;
    @Column(nullable = false, length = 16)
    private String operation;
    @Column(name = "previous_version", nullable = false)
    private long previousVersion;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AdminPhotoOperationJpaEntity() {}

    AdminPhotoOperationJpaEntity(long actorUserId, long ownerUserId, long recordId,
            String operation, long previousVersion, Instant occurredAt) {
        this.actorUserId = actorUserId;
        this.ownerUserId = ownerUserId;
        this.recordId = recordId;
        this.operation = operation;
        this.previousVersion = previousVersion;
        this.occurredAt = occurredAt;
    }

    Long getId() { return id; }
    long getActorUserId() { return actorUserId; }
    long getOwnerUserId() { return ownerUserId; }
    long getRecordId() { return recordId; }
    String getOperation() { return operation; }
    long getPreviousVersion() { return previousVersion; }
    Instant getOccurredAt() { return occurredAt; }
}
