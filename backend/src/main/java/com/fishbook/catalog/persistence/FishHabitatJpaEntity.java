package com.fishbook.catalog.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "fish_habitats")
class FishHabitatJpaEntity {

    @EmbeddedId
    private FishHabitatId id;

    @MapsId("fishSpeciesId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fish_species_id", nullable = false)
    private FishSpeciesJpaEntity fishSpecies;

    protected FishHabitatJpaEntity() {}

    FishHabitatId getId() { return id; }

    FishSpeciesJpaEntity getFishSpecies() { return fishSpecies; }
}
