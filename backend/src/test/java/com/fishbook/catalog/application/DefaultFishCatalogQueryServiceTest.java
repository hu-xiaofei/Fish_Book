package com.fishbook.catalog.application;

import com.fishbook.catalog.domain.FishNotFoundException;
import com.fishbook.catalog.domain.FishPage;
import com.fishbook.catalog.domain.FishRepository;
import com.fishbook.catalog.domain.FishSearchCriteria;
import com.fishbook.catalog.domain.FishSpecies;
import com.fishbook.catalog.domain.HabitatType;
import com.fishbook.catalog.domain.ImageAttribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultFishCatalogQueryServiceTest {

    private RecordingFishRepository repository;
    private DefaultFishCatalogQueryService service;

    @BeforeEach
    void setUp() {
        repository = new RecordingFishRepository();
        repository.page = new FishPage(List.of(fish()), 0, 12, 1, 1);
        repository.families = List.of("鳢科", "鲤科");
        service = new DefaultFishCatalogQueryService(repository);
    }

    @Test
    void trimsSearchAndFamilyAndUsesFixedPageSize() {
        service.search(FishCatalogQuery.from("  黑鱼  ", "  鳢科 ", "lake", "2", null));

        assertThat(repository.lastCriteria).isEqualTo(
                new FishSearchCriteria("黑鱼", "鳢科", HabitatType.LAKE, 2, 12));
    }

    @Test
    void blankSearchAndFamilyBecomeAbsent() {
        service.search(FishCatalogQuery.from("  ", "", null, null, null));

        assertThat(repository.lastCriteria.query()).isNull();
        assertThat(repository.lastCriteria.family()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "not-a-number"})
    void rejectsInvalidPage(String page) {
        assertThatThrownBy(() -> FishCatalogQuery.from(null, null, null, page, null))
                .isInstanceOfSatisfying(InvalidCatalogQueryException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("INVALID_CATALOG_QUERY"));
    }

    @Test
    void rejectsExplicitSizeEvenWhenItIsTwelve() {
        assertThatThrownBy(() -> FishCatalogQuery.from(null, null, null, "0", "12"))
                .isInstanceOf(InvalidCatalogQueryException.class);
    }

    @Test
    void rejectsOversizedOrUnsupportedFilterValues() {
        assertThatThrownBy(() -> FishCatalogQuery.from("鱼".repeat(101), null, null, null, null))
                .isInstanceOf(InvalidCatalogQueryException.class);
        assertThatThrownBy(() -> FishCatalogQuery.from(null, "科".repeat(101), null, null, null))
                .isInstanceOf(InvalidCatalogQueryException.class);
        assertThatThrownBy(() -> FishCatalogQuery.from(null, null, "SEA", null, null))
                .isInstanceOf(InvalidCatalogQueryException.class);
    }

    @Test
    void missingSlugUsesStableErrorCode() {
        assertThatThrownBy(() -> service.getBySlug("missing-fish"))
                .isInstanceOfSatisfying(FishNotFoundException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("FISH_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "Channa-argus", "channa_argus", "channa--argus"})
    void rejectsMalformedSlug(String slug) {
        assertThatThrownBy(() -> service.getBySlug(slug))
                .isInstanceOfSatisfying(InvalidCatalogQueryException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("INVALID_CATALOG_QUERY"));
    }

    @Test
    void mapsSearchResultsToSummaryViews() {
        FishPageView page = service.search(FishCatalogQuery.from(null, null, null, null, null));

        assertThat(page.items().getFirst().aliases()).contains("黑鱼");
        assertThat(page.items().getFirst().habitats()).contains(
                new HabitatOptionView("LAKE", "湖泊"));
        assertThat(page.items().getFirst().imagePath()).isEqualTo(
                "/images/fish/channa-argus.jpg");
    }

    @Test
    void mapsCanonicalSlugDetailsToDetailView() {
        repository.detail = fish();

        FishDetailView detail = service.getBySlug("channa-argus");

        assertThat(detail).isEqualTo(new FishDetailView(
                "channa-argus", "乌鳢", "Channa argus", "鳢科", "Channidae",
                "鳢属", "Channa", List.of("黑鱼"),
                List.of(new HabitatOptionView("LAKE", "湖泊"), new HabitatOptionView("POND", "池塘")),
                "身体细长", "最大可达1.5米", "湖泊和池塘", "东亚", "常见淡水鱼",
                new ImageAttributionView(
                        "/images/fish/channa-argus.jpg", "乌鳢（Channa argus）",
                        "https://commons.wikimedia.org/wiki/File:Channa_argus.jpg", "Test Author",
                        "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/")));
    }

    @Test
    void mapsCanonicalSlugToReferenceView() {
        repository.detail = fish();

        assertThat(service.getReferenceBySlug("channa-argus"))
                .isEqualTo(new FishReferenceView(1L, "channa-argus"));
    }

    @Test
    void returnsReferencesInRequestedSlugOrderWhenRepositoryOrderDiffers() {
        FishSpecies carp = carp();
        repository.bySlugs = List.of(carp, fish());

        assertThat(service.getReferencesBySlugs(List.of("channa-argus", "cyprinus-carpio")))
                .containsExactly(
                        new FishReferenceView(1L, "channa-argus"),
                        new FishReferenceView(2L, "cyprinus-carpio"));
    }

    @Test
    void returnsSummariesInRequestedIdOrderWhenRepositoryOrderDiffers() {
        FishSpecies carp = carp();
        repository.byIds = List.of(fish(), carp);

        assertThat(service.getSummariesByIds(List.of(2L, 1L)))
                .extracting(FishSummaryView::slug)
                .containsExactly("cyprinus-carpio", "channa-argus");
    }

    @Test
    void rejectsMissingReferenceSlug() {
        assertThatThrownBy(() -> service.getReferencesBySlugs(List.of("missing-fish")))
                .isInstanceOf(FishNotFoundException.class);
    }

    @Test
    void rejectsMissingSummaryIdAsAnImpossibleState() {
        assertThatThrownBy(() -> service.getSummariesByIds(List.of(99L)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedReferenceBatchArguments() {
        assertThatThrownBy(() -> service.getReferencesBySlugs(null))
                .isInstanceOf(InvalidCatalogQueryException.class);
        assertThatThrownBy(() -> service.getReferencesBySlugs(Arrays.asList("channa-argus", null)))
                .isInstanceOf(InvalidCatalogQueryException.class);
        assertThatThrownBy(() -> service.getReferencesBySlugs(List.of("channa-argus", "channa-argus")))
                .isInstanceOf(InvalidCatalogQueryException.class);
    }

    @Test
    void rejectsMalformedSummaryBatchArguments() {
        assertThatThrownBy(() -> service.getSummariesByIds(null))
                .isInstanceOf(InvalidCatalogQueryException.class);
        assertThatThrownBy(() -> service.getSummariesByIds(Arrays.asList(1L, null)))
                .isInstanceOf(InvalidCatalogQueryException.class);
    }

    @Test
    void returnsFamiliesInRepositoryOrderAndHabitatsInEnumOrder() {
        FishFilterOptionsView options = service.getFilterOptions();

        assertThat(options.families()).containsExactly("鳢科", "鲤科");
        assertThat(options.habitats())
                .extracting(HabitatOptionView::code)
                .containsExactly("RIVER", "LAKE", "RESERVOIR", "POND", "STREAM");
    }

    private static FishSpecies fish() {
        Instant now = Instant.parse("2026-08-11T00:00:00Z");
        return new FishSpecies(
                1L, "channa-argus", "乌鳢", "Channa argus", "鳢科", "Channidae", "鳢属", "Channa",
                List.of("黑鱼"), EnumSet.of(HabitatType.LAKE, HabitatType.POND),
                "身体细长", "最大可达1.5米", "湖泊和池塘", "东亚", "常见淡水鱼",
                new ImageAttribution(
                        "/images/fish/channa-argus.jpg", "乌鳢（Channa argus）",
                        "https://commons.wikimedia.org/wiki/File:Channa_argus.jpg", "Test Author",
                        "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
                1, now, now);
    }

    private static FishSpecies carp() {
        Instant now = Instant.parse("2026-08-11T00:00:00Z");
        return new FishSpecies(
                2L, "cyprinus-carpio", "鲤", "Cyprinus carpio", "鲤科", "Cyprinidae", "鲤属", "Cyprinus",
                List.of("鲤鱼"), EnumSet.of(HabitatType.RIVER, HabitatType.LAKE),
                "身体粗壮", "最大可达1米", "河流和湖泊", "欧亚大陆", "常见淡水鱼",
                new ImageAttribution(
                        "/images/fish/cyprinus-carpio.jpg", "鲤（Cyprinus carpio）",
                        "https://commons.wikimedia.org/wiki/File:Cyprinus_carpio.jpg", "Test Author",
                        "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
                2, now, now);
    }

    private static final class RecordingFishRepository implements FishRepository {

        private FishSearchCriteria lastCriteria;
        private FishPage page;
        private FishSpecies detail;
        private List<String> families = List.of();
        private List<FishSpecies> byIds = List.of();
        private List<FishSpecies> bySlugs = List.of();

        @Override
        public FishPage search(FishSearchCriteria criteria) {
            lastCriteria = criteria;
            return page;
        }

        @Override
        public Optional<FishSpecies> findBySlug(String slug) {
            return Optional.ofNullable(detail);
        }

        @Override
        public List<FishSpecies> findAllByIds(List<Long> ids) {
            return byIds;
        }

        @Override
        public List<FishSpecies> findAllBySlugs(List<String> slugs) {
            return bySlugs;
        }

        @Override
        public List<String> findAvailableFamilies() {
            return families;
        }
    }
}
