package com.fishbook.catchlog.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "catch_records")
class CatchRecordJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "fish_species_id", nullable = false)
    private long fishSpeciesId;

    @Column(name = "caught_on", nullable = false)
    private LocalDate caughtOn;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(name = "length_cm", precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "weight_g", precision = 10, scale = 2)
    private BigDecimal weightG;

    @Column(length = 100)
    private String method;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "photo_object_key", length = 512)
    private String photoObjectKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CatchRecordJpaEntity() {}

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    long getUserId() {
        return userId;
    }

    void setUserId(long userId) {
        this.userId = userId;
    }

    long getFishSpeciesId() {
        return fishSpeciesId;
    }

    void setFishSpeciesId(long fishSpeciesId) {
        this.fishSpeciesId = fishSpeciesId;
    }

    LocalDate getCaughtOn() {
        return caughtOn;
    }

    void setCaughtOn(LocalDate caughtOn) {
        this.caughtOn = caughtOn;
    }

    String getLocation() {
        return location;
    }

    void setLocation(String location) {
        this.location = location;
    }

    BigDecimal getLengthCm() {
        return lengthCm;
    }

    void setLengthCm(BigDecimal lengthCm) {
        this.lengthCm = lengthCm;
    }

    BigDecimal getWeightG() {
        return weightG;
    }

    void setWeightG(BigDecimal weightG) {
        this.weightG = weightG;
    }

    String getMethod() {
        return method;
    }

    void setMethod(String method) {
        this.method = method;
    }

    String getNotes() {
        return notes;
    }

    void setNotes(String notes) {
        this.notes = notes;
    }

    String getPhotoObjectKey() {
        return photoObjectKey;
    }

    void setPhotoObjectKey(String photoObjectKey) {
        this.photoObjectKey = photoObjectKey;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
