package com.fishbook.catalog.web.dto;

import com.fishbook.catalog.application.FishDetailView;
import java.util.List;

public record FishDetailResponse(
        String slug,
        String commonNameZh,
        String scientificName,
        String familyNameZh,
        String familyScientificName,
        String genusNameZh,
        String genusScientificName,
        List<String> aliases,
        List<HabitatOptionResponse> habitats,
        String appearance,
        String sizeDescription,
        String habitatDescription,
        String distribution,
        String description,
        ImageAttributionResponse image) {

    public FishDetailResponse {
        aliases = List.copyOf(aliases);
        habitats = List.copyOf(habitats);
    }

    public static FishDetailResponse from(FishDetailView view) {
        return new FishDetailResponse(
                view.slug(),
                view.commonNameZh(),
                view.scientificName(),
                view.familyNameZh(),
                view.familyScientificName(),
                view.genusNameZh(),
                view.genusScientificName(),
                view.aliases(),
                view.habitats().stream().map(HabitatOptionResponse::from).toList(),
                view.appearance(),
                view.sizeDescription(),
                view.habitatDescription(),
                view.distribution(),
                view.description(),
                ImageAttributionResponse.from(view.image()));
    }
}
