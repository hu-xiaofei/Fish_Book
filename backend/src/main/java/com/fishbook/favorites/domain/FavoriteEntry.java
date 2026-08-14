package com.fishbook.favorites.domain;

import java.time.Instant;

public record FavoriteEntry(long fishId, Instant favoritedAt) {}
