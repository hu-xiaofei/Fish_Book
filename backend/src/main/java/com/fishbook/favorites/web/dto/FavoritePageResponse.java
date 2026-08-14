package com.fishbook.favorites.web.dto;

import com.fishbook.favorites.application.FavoritePageView;
import java.util.List;

public record FavoritePageResponse(
        List<FavoriteSummaryResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public FavoritePageResponse {
        items = List.copyOf(items);
    }

    public static FavoritePageResponse from(FavoritePageView view) {
        return new FavoritePageResponse(
                view.items().stream().map(FavoriteSummaryResponse::from).toList(),
                view.page(),
                view.size(),
                view.totalItems(),
                view.totalPages());
    }
}
