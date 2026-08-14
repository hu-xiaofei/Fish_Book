package com.fishbook.favorites.web.dto;

import com.fishbook.catalog.web.dto.HabitatOptionResponse;
import com.fishbook.favorites.application.FavoriteSummaryView;
import java.time.Instant;
import java.util.List;

public record FavoriteSummaryResponse(
        String slug,
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        List<String> aliases,
        List<HabitatOptionResponse> habitats,
        String imagePath,
        String imageAltText,
        Instant favoritedAt) {

    public FavoriteSummaryResponse {
        aliases = List.copyOf(aliases);
        habitats = List.copyOf(habitats);
    }

    public static FavoriteSummaryResponse from(FavoriteSummaryView view) {
        return new FavoriteSummaryResponse(
                view.slug(),
                view.commonNameZh(),
                view.scientificName(),
                view.familyNameZh(),
                view.aliases(),
                view.habitats().stream().map(HabitatOptionResponse::from).toList(),
                view.imagePath(),
                view.imageAltText(),
                view.favoritedAt());
    }
}
