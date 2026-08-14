package com.fishbook.favorites.application;

import com.fishbook.catalog.application.FishCatalogQueryService;
import com.fishbook.catalog.application.FishReferenceView;
import com.fishbook.catalog.application.FishSummaryView;
import com.fishbook.favorites.domain.FavoriteEntry;
import com.fishbook.favorites.domain.FavoritePage;
import com.fishbook.favorites.domain.FavoriteRepository;
import com.fishbook.identity.application.ProfileApplicationService;
import com.fishbook.identity.application.UserView;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultFavoriteApplicationService implements FavoriteApplicationService {

    private static final int PAGE_SIZE = 12;
    private static final int MAX_STATUS_SLUGS = 12;
    private static final String CANONICAL_SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

    private final ProfileApplicationService profileApplicationService;
    private final FishCatalogQueryService fishCatalogQueryService;
    private final FavoriteRepository favoriteRepository;

    public DefaultFavoriteApplicationService(
            ProfileApplicationService profileApplicationService,
            FishCatalogQueryService fishCatalogQueryService,
            FavoriteRepository favoriteRepository) {
        this.profileApplicationService = Objects.requireNonNull(profileApplicationService);
        this.fishCatalogQueryService = Objects.requireNonNull(fishCatalogQueryService);
        this.favoriteRepository = Objects.requireNonNull(favoriteRepository);
    }

    @Override
    @Transactional
    public void add(String authenticatedEmail, String fishSlug) {
        validateSlug(fishSlug);
        UserView user = currentUser(authenticatedEmail);
        FishReferenceView fish = fishCatalogQueryService.getReferenceBySlug(fishSlug);
        favoriteRepository.add(user.id(), fish.id(), Instant.now());
    }

    @Override
    @Transactional
    public void remove(String authenticatedEmail, String fishSlug) {
        validateSlug(fishSlug);
        UserView user = currentUser(authenticatedEmail);
        FishReferenceView fish = fishCatalogQueryService.getReferenceBySlug(fishSlug);
        favoriteRepository.remove(user.id(), fish.id());
    }

    @Override
    public FavoritePageView list(String authenticatedEmail, int page) {
        if (page < 0) {
            throw new InvalidFavoriteQueryException("page must be a non-negative integer");
        }
        UserView user = currentUser(authenticatedEmail);
        FavoritePage favoritePage = favoriteRepository.findByUserId(user.id(), page, PAGE_SIZE);
        List<Long> fishIds = favoritePage.items().stream().map(FavoriteEntry::fishId).toList();
        Map<Long, FishSummaryView> summariesById = summariesById(fishIds);
        List<FavoriteSummaryView> items = favoritePage.items().stream()
                .map(entry -> toFavoriteSummary(summariesById.get(entry.fishId()), entry.favoritedAt()))
                .toList();
        return new FavoritePageView(
                items,
                favoritePage.page(),
                favoritePage.size(),
                favoritePage.totalItems(),
                favoritePage.totalPages());
    }

    @Override
    public List<FavoriteStatusView> statuses(String authenticatedEmail, List<String> fishSlugs) {
        List<String> normalizedSlugs = normalizeStatusSlugs(fishSlugs);
        UserView user = currentUser(authenticatedEmail);
        List<FishReferenceView> references = fishCatalogQueryService.getReferencesBySlugs(normalizedSlugs);
        Set<Long> fishIds = references.stream().map(FishReferenceView::id).collect(java.util.stream.Collectors.toSet());
        Set<Long> favoritedFishIds = favoriteRepository.findFavoritedFishIds(user.id(), fishIds);
        return references.stream()
                .map(reference -> new FavoriteStatusView(
                        reference.slug(), favoritedFishIds.contains(reference.id())))
                .toList();
    }

    private UserView currentUser(String authenticatedEmail) {
        return profileApplicationService.currentUser(authenticatedEmail);
    }

    private Map<Long, FishSummaryView> summariesById(List<Long> fishIds) {
        Map<Long, FishSummaryView> summariesById = new HashMap<>();
        List<FishSummaryView> summaries = fishCatalogQueryService.getSummariesByIds(fishIds);
        for (int index = 0; index < fishIds.size(); index++) {
            summariesById.put(fishIds.get(index), summaries.get(index));
        }
        return summariesById;
    }

    private FavoriteSummaryView toFavoriteSummary(FishSummaryView summary, Instant favoritedAt) {
        if (summary == null) {
            throw new IllegalStateException("favorite fish must exist");
        }
        return new FavoriteSummaryView(
                summary.slug(),
                summary.commonNameZh(),
                summary.scientificName(),
                summary.familyNameZh(),
                summary.aliases(),
                summary.habitats(),
                summary.imagePath(),
                summary.imageAltText(),
                favoritedAt);
    }

    private List<String> normalizeStatusSlugs(List<String> fishSlugs) {
        if (fishSlugs == null) {
            throw new InvalidFavoriteQueryException("fish slugs must not be null");
        }
        LinkedHashSet<String> uniqueSlugs = new LinkedHashSet<>();
        for (String fishSlug : fishSlugs) {
            validateSlug(fishSlug);
            uniqueSlugs.add(fishSlug);
        }
        if (uniqueSlugs.size() > MAX_STATUS_SLUGS) {
            throw new InvalidFavoriteQueryException("at most 12 unique fish slugs are allowed");
        }
        return List.copyOf(uniqueSlugs);
    }

    private void validateSlug(String slug) {
        if (slug == null || !slug.matches(CANONICAL_SLUG_PATTERN)) {
            throw new InvalidFavoriteQueryException("slug must be canonical and nonblank");
        }
    }
}
