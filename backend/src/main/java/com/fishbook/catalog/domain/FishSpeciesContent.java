package com.fishbook.catalog.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record FishSpeciesContent(
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
        int displayOrder) {

    public FishSpeciesContent {
        commonNameZh = required(commonNameZh, 100, "commonNameZh");
        scientificName = required(scientificName, 160, "scientificName");
        familyNameZh = required(familyNameZh, 100, "familyNameZh");
        familyScientificName = required(familyScientificName, 160, "familyScientificName");
        genusNameZh = required(genusNameZh, 100, "genusNameZh");
        genusScientificName = required(genusScientificName, 160, "genusScientificName");
        appearance = required(appearance, 10_000, "appearance");
        sizeDescription = required(sizeDescription, 10_000, "sizeDescription");
        habitatDescription = required(habitatDescription, 10_000, "habitatDescription");
        distribution = required(distribution, 10_000, "distribution");
        description = required(description, 10_000, "description");
        aliases = normalizedAliases(aliases);
        habitats = immutableHabitats(habitats);
        if (image == null) {
            throw new InvalidFishSpeciesException("image must not be null");
        }
        if (displayOrder <= 0) {
            throw new InvalidFishSpeciesException("displayOrder must be positive");
        }
    }

    private static String required(String value, int maxCodePoints, String field) {
        if (value == null) {
            throw new InvalidFishSpeciesException(field + " must not be null");
        }
        String normalized = value.strip();
        if (normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new InvalidFishSpeciesException(field + " has an invalid length");
        }
        return normalized;
    }

    private static List<String> normalizedAliases(List<String> values) {
        if (values == null) {
            throw new InvalidFishSpeciesException("aliases must not be null");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String alias = required(value, 100, "alias");
            if (!normalized.add(alias)) {
                throw new InvalidFishSpeciesException("aliases must be unique after normalization");
            }
        }
        return List.copyOf(normalized);
    }

    private static Set<HabitatType> immutableHabitats(Set<HabitatType> values) {
        if (values == null) {
            throw new InvalidFishSpeciesException("habitats must not be null");
        }
        if (values.isEmpty() || values.stream().anyMatch(value -> value == null)) {
            throw new InvalidFishSpeciesException("habitats must contain at least one value");
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
