package com.fishbook.favorites.application;

import java.util.List;

public interface FavoriteApplicationService {
    void add(String authenticatedEmail, String fishSlug);

    void remove(String authenticatedEmail, String fishSlug);

    FavoritePageView list(String authenticatedEmail, int page);

    List<FavoriteStatusView> statuses(String authenticatedEmail, List<String> fishSlugs);
}
