package com.fishbook.catalog.application;

import java.util.List;

public record FishFilterOptionsView(
        List<String> families,
        List<HabitatOptionView> habitats) {

    public FishFilterOptionsView {
        families = List.copyOf(families);
        habitats = List.copyOf(habitats);
    }
}
