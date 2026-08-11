package com.fishbook.catalog.web.dto;

import com.fishbook.catalog.application.FishFilterOptionsView;
import java.util.List;

public record FishFilterOptionsResponse(
        List<String> families,
        List<HabitatOptionResponse> habitats) {

    public FishFilterOptionsResponse {
        families = List.copyOf(families);
        habitats = List.copyOf(habitats);
    }

    public static FishFilterOptionsResponse from(FishFilterOptionsView view) {
        return new FishFilterOptionsResponse(
                view.families(),
                view.habitats().stream().map(HabitatOptionResponse::from).toList());
    }
}
