package com.fishbook.catalog.application;

import java.util.List;

public record FishDetailView(
        String slug,
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        String familyScientificName,
        String genusNameZh,
        String genusScientificName,
        List<String> aliases,
        List<HabitatOptionView> habitats,
        String appearance,
        String sizeDescription,
        String habitatDescription,
        String distribution,
        String description,
        ImageAttributionView image) {

    public FishDetailView {
        aliases = List.copyOf(aliases);
        habitats = List.copyOf(habitats);
    }
}
