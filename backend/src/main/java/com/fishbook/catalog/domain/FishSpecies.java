package com.fishbook.catalog.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record FishSpecies(
        Long id,
        String slug,
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        String familyScientificName,
        String genusNameZh,
        String genusScientificName,
        List<String> aliases,
        Set<HabitatType> habitats,
        String appearance,
        String sizeDescription,
        String habitatDescription,
        String distribution,
        String description,
        ImageAttribution image,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt) {

    public FishSpecies {
        requireText(slug, "slug");
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("slug must be canonical");
        }
        requireText(commonNameZh, "commonNameZh");
        requireText(scientificName, "scientificName");
        requireText(familyNameZh, "familyNameZh");
        requireText(familyScientificName, "familyScientificName");
        requireText(genusNameZh, "genusNameZh");
        requireText(genusScientificName, "genusScientificName");
        requireText(appearance, "appearance");
        requireText(sizeDescription, "sizeDescription");
        requireText(habitatDescription, "habitatDescription");
        requireText(distribution, "distribution");
        requireText(description, "description");
        Objects.requireNonNull(image, "image must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases must not be null"));
        if (aliases.stream().anyMatch(alias -> alias == null || alias.isBlank())) {
            throw new IllegalArgumentException("aliases must not contain blanks");
        }
        if (aliases.stream().distinct().count() != aliases.size()) {
            throw new IllegalArgumentException("aliases must be unique");
        }
        habitats = Set.copyOf(Objects.requireNonNull(habitats, "habitats must not be null"));
        if (habitats.isEmpty()) {
            throw new IllegalArgumentException("habitats must not be empty");
        }
        if (displayOrder <= 0) {
            throw new IllegalArgumentException("displayOrder must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
