package com.fishbook.administration.application;

import java.util.List;

public record AdminFishPageView(
        List<AdminFishSummaryView> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public AdminFishPageView {
        items = List.copyOf(items);
    }
}
