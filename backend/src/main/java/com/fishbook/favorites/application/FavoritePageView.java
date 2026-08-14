package com.fishbook.favorites.application;

import java.util.List;

public record FavoritePageView(
        List<FavoriteSummaryView> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public FavoritePageView {
        items = List.copyOf(items);
    }
}
