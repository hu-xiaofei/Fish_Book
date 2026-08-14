package com.fishbook.favorites.application;

import com.fishbook.catalog.application.FishCatalogQuery;
import com.fishbook.catalog.application.FishCatalogQueryService;
import com.fishbook.catalog.application.FishDetailView;
import com.fishbook.catalog.application.FishFilterOptionsView;
import com.fishbook.catalog.application.FishPageView;
import com.fishbook.catalog.application.FishReferenceView;
import com.fishbook.catalog.application.FishSummaryView;
import com.fishbook.catalog.application.HabitatOptionView;
import com.fishbook.favorites.domain.FavoriteEntry;
import com.fishbook.favorites.domain.FavoritePage;
import com.fishbook.favorites.domain.FavoriteRepository;
import com.fishbook.identity.application.ProfileApplicationService;
import com.fishbook.identity.application.UserView;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultFavoriteApplicationServiceTest {

    private RecordingProfileService profiles;
    private RecordingCatalogService catalog;
    private RecordingFavoriteRepository favorites;
    private FavoriteApplicationService service;

    @BeforeEach
    void setUp() {
        profiles = new RecordingProfileService();
        catalog = new RecordingCatalogService();
        favorites = new RecordingFavoriteRepository();
        service = new DefaultFavoriteApplicationService(profiles, catalog, favorites);
    }

    @Test
    void addsFavoriteForTheAuthenticatedUser() {
        service.add("reader@example.com", "channa-argus");

        assertThat(favorites.addedUserId).isEqualTo(41L);
        assertThat(favorites.addedFishId).isEqualTo(1L);
        assertThat(favorites.addedAt).isNotNull();
        assertThat(profiles.requestedEmail).isEqualTo("reader@example.com");
    }

    @Test
    void removesFavoriteForTheAuthenticatedUser() {
        service.remove("reader@example.com", "cyprinus-carpio");

        assertThat(favorites.removedUserId).isEqualTo(41L);
        assertThat(favorites.removedFishId).isEqualTo(2L);
        assertThat(profiles.requestedEmail).isEqualTo("reader@example.com");
    }

    @Test
    void listsFavoriteSummariesInFavoriteOrderWithTheirSavedTimes() {
        Instant newer = Instant.parse("2026-08-14T12:00:00Z");
        Instant older = Instant.parse("2026-08-13T12:00:00Z");
        favorites.page = new FavoritePage(
                List.of(new FavoriteEntry(2L, newer), new FavoriteEntry(1L, older)),
                0, 12, 14, 2);

        FavoritePageView page = service.list("reader@example.com", 0);

        assertThat(favorites.lastPage).isZero();
        assertThat(favorites.lastSize).isEqualTo(12);
        assertThat(page.items()).containsExactly(
                new FavoriteSummaryView(
                        "cyprinus-carpio", "鲤", "Cyprinus carpio", "鲤科",
                        List.of("鲤鱼"), List.of(new HabitatOptionView("RIVER", "河流")),
                        "/images/fish/cyprinus-carpio.jpg", "鲤", newer),
                new FavoriteSummaryView(
                        "channa-argus", "乌鳢", "Channa argus", "鳢科",
                        List.of("黑鱼"), List.of(new HabitatOptionView("LAKE", "湖泊")),
                        "/images/fish/channa-argus.jpg", "乌鳢", older));
        assertThat(page).extracting(
                FavoritePageView::page,
                FavoritePageView::size,
                FavoritePageView::totalItems,
                FavoritePageView::totalPages)
                .containsExactly(0, 12, 14L, 2);
    }

    @Test
    void returnsStatusesInFirstSeenSlugOrderWithoutDuplicates() {
        favorites.favoritedFishIds = Set.of(2L);

        List<FavoriteStatusView> statuses = service.statuses(
                "reader@example.com",
                List.of("cyprinus-carpio", "channa-argus", "cyprinus-carpio"));

        assertThat(statuses).containsExactly(
                new FavoriteStatusView("cyprinus-carpio", true),
                new FavoriteStatusView("channa-argus", false));
        assertThat(catalog.lastReferenceSlugs)
                .containsExactly("cyprinus-carpio", "channa-argus");
    }

    @Test
    void rejectsNegativeFavoritePageWithTheStableErrorCode() {
        assertThatThrownBy(() -> service.list("reader@example.com", -1))
                .isInstanceOfSatisfying(InvalidFavoriteQueryException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("INVALID_FAVORITE_QUERY"));
    }

    @Test
    void usesTheFixedTwelveItemPageSize() {
        service.list("reader@example.com", 1);

        assertThat(favorites.lastSize).isEqualTo(12);
    }

    @Test
    void rejectsMoreThanTwelveUniqueStatusSlugs() {
        List<String> slugs = List.of(
                "fish-1", "fish-2", "fish-3", "fish-4", "fish-5", "fish-6",
                "fish-7", "fish-8", "fish-9", "fish-10", "fish-11", "fish-12", "fish-13");

        assertThatThrownBy(() -> service.statuses("reader@example.com", slugs))
                .isInstanceOfSatisfying(InvalidFavoriteQueryException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("INVALID_FAVORITE_QUERY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Channa-argus", "channa_argus", "channa--argus"})
    void rejectsNonCanonicalFavoriteSlugs(String slug) {
        assertThatThrownBy(() -> service.add("reader@example.com", slug))
                .isInstanceOfSatisfying(InvalidFavoriteQueryException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("INVALID_FAVORITE_QUERY"));
    }

    private static final class RecordingProfileService implements ProfileApplicationService {
        private String requestedEmail;

        @Override
        public UserView currentUser(String normalizedEmail) {
            requestedEmail = normalizedEmail;
            return new UserView(41L, normalizedEmail, "reader", "USER");
        }

        @Override
        public UserView updateNickname(String normalizedEmail, String nickname) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingCatalogService implements FishCatalogQueryService {
        private final Map<String, FishReferenceView> references = Map.of(
                "channa-argus", new FishReferenceView(1L, "channa-argus"),
                "cyprinus-carpio", new FishReferenceView(2L, "cyprinus-carpio"));
        private final Map<Long, FishSummaryView> summaries = Map.of(
                1L, new FishSummaryView(
                        "channa-argus", "乌鳢", "Channa argus", "鳢科",
                        List.of("黑鱼"), List.of(new HabitatOptionView("LAKE", "湖泊")),
                        "/images/fish/channa-argus.jpg", "乌鳢"),
                2L, new FishSummaryView(
                        "cyprinus-carpio", "鲤", "Cyprinus carpio", "鲤科",
                        List.of("鲤鱼"), List.of(new HabitatOptionView("RIVER", "河流")),
                        "/images/fish/cyprinus-carpio.jpg", "鲤"));
        private List<String> lastReferenceSlugs = List.of();

        @Override
        public FishPageView search(FishCatalogQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FishDetailView getBySlug(String slug) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FishReferenceView getReferenceBySlug(String slug) {
            return references.get(slug);
        }

        @Override
        public List<FishReferenceView> getReferencesBySlugs(List<String> slugs) {
            lastReferenceSlugs = List.copyOf(slugs);
            return slugs.stream().map(references::get).toList();
        }

        @Override
        public List<FishSummaryView> getSummariesByIds(List<Long> ids) {
            return ids.stream().map(summaries::get).toList();
        }

        @Override
        public FishFilterOptionsView getFilterOptions() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingFavoriteRepository implements FavoriteRepository {
        private long addedUserId;
        private long addedFishId;
        private Instant addedAt;
        private long removedUserId;
        private long removedFishId;
        private int lastPage;
        private int lastSize;
        private FavoritePage page = new FavoritePage(List.of(), 0, 12, 0, 0);
        private Set<Long> favoritedFishIds = Set.of();

        @Override
        public void add(long userId, long fishId, Instant now) {
            addedUserId = userId;
            addedFishId = fishId;
            addedAt = now;
        }

        @Override
        public void remove(long userId, long fishId) {
            removedUserId = userId;
            removedFishId = fishId;
        }

        @Override
        public FavoritePage findByUserId(long userId, int page, int size) {
            lastPage = page;
            lastSize = size;
            return this.page;
        }

        @Override
        public Set<Long> findFavoritedFishIds(long userId, Set<Long> fishIds) {
            return new HashSet<>(favoritedFishIds);
        }
    }
}
