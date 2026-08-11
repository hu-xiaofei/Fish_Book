package com.fishbook.catalog.persistence;

import com.fishbook.catalog.domain.HabitatType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
class FishHabitatId implements Serializable {

    @Column(name = "fish_species_id", nullable = false)
    private Long fishSpeciesId;

    @Enumerated(EnumType.STRING)
    @Column(name = "habitat_code", nullable = false, length = 20)
    private HabitatType habitatCode;

    protected FishHabitatId() {}

    HabitatType getHabitatCode() { return habitatCode; }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FishHabitatId that)) {
            return false;
        }
        return Objects.equals(fishSpeciesId, that.fishSpeciesId)
                && habitatCode == that.habitatCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fishSpeciesId, habitatCode);
    }
}
