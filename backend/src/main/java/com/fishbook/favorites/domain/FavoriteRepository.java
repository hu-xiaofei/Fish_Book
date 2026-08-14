package com.fishbook.favorites.domain;

import java.time.Instant;
import java.util.Set;

public interface FavoriteRepository {
    void add(long userId, long fishId, Instant now);

    void remove(long userId, long fishId);

    FavoritePage findByUserId(long userId, int page, int size);

    Set<Long> findFavoritedFishIds(long userId, Set<Long> fishIds);
}
