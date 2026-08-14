package com.fishbook.favorites.web.dto;

import com.fishbook.favorites.application.FavoriteStatusView;
import java.util.List;

public record FavoriteStatusResponse(List<FavoriteStatusItemResponse> items) {

    public FavoriteStatusResponse {
        items = List.copyOf(items);
    }

    public static FavoriteStatusResponse from(List<FavoriteStatusView> views) {
        return new FavoriteStatusResponse(
                views.stream().map(FavoriteStatusItemResponse::from).toList());
    }
}
