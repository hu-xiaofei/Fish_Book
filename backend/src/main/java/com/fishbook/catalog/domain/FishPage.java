package com.fishbook.catalog.domain;

import java.util.List;

public record FishPage(
        List<FishSpecies> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
    public FishPage {
        items = List.copyOf(items);
    }
}
