package com.fishbook.catalog.application;

import java.util.List;

public record FishPageView(
        List<FishSummaryView> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public FishPageView {
        items = List.copyOf(items);
    }
}
