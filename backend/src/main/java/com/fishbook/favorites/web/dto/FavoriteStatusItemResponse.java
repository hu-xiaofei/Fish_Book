package com.fishbook.favorites.web.dto;

import com.fishbook.favorites.application.FavoriteStatusView;

public record FavoriteStatusItemResponse(String fishSlug, boolean favorited) {

    public static FavoriteStatusItemResponse from(FavoriteStatusView view) {
        return new FavoriteStatusItemResponse(view.fishSlug(), view.favorited());
    }
}
