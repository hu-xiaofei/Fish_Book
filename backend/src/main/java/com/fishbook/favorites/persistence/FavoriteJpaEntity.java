package com.fishbook.favorites.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "favorites")
class FavoriteJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "fish_species_id", nullable = false)
    private long fishSpeciesId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FavoriteJpaEntity() {}

    Long getId() {
        return id;
    }

    long getUserId() {
        return userId;
    }

    long getFishSpeciesId() {
        return fishSpeciesId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
