package com.fishbook.catalog.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "fish_aliases")
class FishAliasJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fish_species_id", nullable = false)
    private FishSpeciesJpaEntity fishSpecies;

    @Column(nullable = false, length = 100)
    private String alias;

    protected FishAliasJpaEntity() {}

    Long getId() { return id; }

    FishSpeciesJpaEntity getFishSpecies() { return fishSpecies; }

    String getAlias() { return alias; }
}
