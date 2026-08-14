package com.fishbook.favorites.domain;

import java.util.List;

public record FavoritePage(
        List<FavoriteEntry> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {}
