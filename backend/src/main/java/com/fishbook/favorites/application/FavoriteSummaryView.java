package com.fishbook.favorites.application;

import com.fishbook.catalog.application.HabitatOptionView;
import java.time.Instant;
import java.util.List;

public record FavoriteSummaryView(
        String slug,
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        List<String> aliases,
        List<HabitatOptionView> habitats,
        String imagePath,
        String imageAltText,
        Instant favoritedAt) {

    public FavoriteSummaryView {
        aliases = List.copyOf(aliases);
        habitats = List.copyOf(habitats);
    }
}
