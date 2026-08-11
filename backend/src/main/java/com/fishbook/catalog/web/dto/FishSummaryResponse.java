package com.fishbook.catalog.web.dto;

import com.fishbook.catalog.application.FishSummaryView;
import java.util.List;

public record FishSummaryResponse(
        String slug,
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        List<String> aliases,
        List<HabitatOptionResponse> habitats,
        String imagePath,
        String imageAltText) {

    public FishSummaryResponse {
        aliases = List.copyOf(aliases);
        habitats = List.copyOf(habitats);
    }

    public static FishSummaryResponse from(FishSummaryView view) {
        return new FishSummaryResponse(
                view.slug(),
                view.commonNameZh(),
                view.scientificName(),
                view.familyNameZh(),
                view.aliases(),
                view.habitats().stream().map(HabitatOptionResponse::from).toList(),
                view.imagePath(),
                view.imageAltText());
    }
}
