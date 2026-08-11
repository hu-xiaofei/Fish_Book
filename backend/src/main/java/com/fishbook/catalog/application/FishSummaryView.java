package com.fishbook.catalog.application;

import java.util.List;

public record FishSummaryView(
        String slug,
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        List<String> aliases,
        List<HabitatOptionView> habitats,
        String imagePath,
        String imageAltText) {

    public FishSummaryView {
        aliases = List.copyOf(aliases);
        habitats = List.copyOf(habitats);
    }
}
