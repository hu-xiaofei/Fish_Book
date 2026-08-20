package com.fishbook.catchlog.application;

import com.fishbook.catalog.application.FishCatalogQuery;
import com.fishbook.catalog.application.FishCatalogQueryService;
import com.fishbook.catalog.application.FishDetailView;
import com.fishbook.catalog.application.FishFilterOptionsView;
import com.fishbook.catalog.application.FishPageView;
import com.fishbook.catalog.application.FishReferenceView;
import com.fishbook.catalog.application.FishSummaryView;
import com.fishbook.catalog.application.HabitatOptionView;
import com.fishbook.catalog.domain.FishNotFoundException;
import com.fishbook.catchlog.domain.CatchRecord;
import com.fishbook.catchlog.domain.CatchRecordDetails;
import com.fishbook.catchlog.domain.CatchRecordNotFoundException;
import com.fishbook.catchlog.domain.CatchRecordPage;
import com.fishbook.catchlog.domain.CatchRecordRepository;
import com.fishbook.catchlog.domain.InvalidCatchRecordException;
import com.fishbook.identity.application.ProfileApplicationService;
import com.fishbook.identity.application.UserView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCatchRecordApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final Clock SHANGHAI_BOUNDARY_CLOCK = Clock.fixed(
            Instant.parse("2026-08-19T16:30:00Z"), ZoneId.of("UTC"));

    private RecordingProfileService profiles;
    private RecordingCatalogService catalog;
    private RecordingCatchRecordRepository repository;
    private CatchRecordApplicationService service;

    @BeforeEach
    void setUp() {
        profiles = new RecordingProfileService();
        catalog = new RecordingCatalogService();
        repository = new RecordingCatchRecordRepository();
        service = new DefaultCatchRecordApplicationService(profiles, catalog, repository, CLOCK);
    }

    @Test
    void createsARecordForTheAuthenticatedUserAndResolvedFish() {
        CatchRecordDetailView created = service.create(
                "angler@example.com",
                command("channa-argus", LocalDate.parse("2026-08-20"), "  城郊水库  "));

        assertThat(profiles.requestedEmail).isEqualTo("angler@example.com");
        assertThat(repository.saved.userId()).isEqualTo(41L);
        assertThat(repository.saved.details()).isEqualTo(new CatchRecordDetails(
                1L, LocalDate.parse("2026-08-20"), "城郊水库",
                new BigDecimal("42.5"), new BigDecimal("1350"), "路亚", "傍晚近岸中鱼"));
        assertThat(repository.saved.createdAt()).isEqualTo(Instant.parse("2026-08-20T02:00:00Z"));
        assertThat(repository.saved.updatedAt()).isEqualTo(Instant.parse("2026-08-20T02:00:00Z"));
        assertThat(created).extracting(
                CatchRecordDetailView::id,
                CatchRecordDetailView::fishSlug,
                CatchRecordDetailView::commonNameZh,
                CatchRecordDetailView::hasPhoto)
                .containsExactly(31L, "channa-argus", "乌鳢", false);
    }

    @Test
    void hidesAnotherUsersRecordBehindTheSameNotFoundError() {
        repository.ownedRecord = Optional.empty();

        assertThatThrownBy(() -> service.get("angler@example.com", 99L))
                .isInstanceOfSatisfying(CatchRecordNotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("CATCH_RECORD_NOT_FOUND"));
        assertThat(repository.lastOwnedUserId).isEqualTo(41L);
    }

    @Test
    void listsRecordsInRepositoryOrderWithOneBatchedFishSummaryLookup() {
        repository.page = new CatchRecordPage(List.of(
                record(31L, 2L, LocalDate.parse("2026-08-20"), null),
                record(30L, 1L, LocalDate.parse("2026-08-19"), "private/key")),
                1, 20, 22, 2);

        CatchRecordPageView page = service.list("angler@example.com", 1);

        assertThat(repository.lastListUserId).isEqualTo(41L);
        assertThat(repository.lastPage).isEqualTo(1);
        assertThat(repository.lastSize).isEqualTo(20);
        assertThat(catalog.summaryLookupCount).isEqualTo(1);
        assertThat(catalog.lastSummaryIds).containsExactly(2L, 1L);
        assertThat(page.items()).extracting(
                CatchRecordSummaryView::id,
                CatchRecordSummaryView::fishSlug,
                CatchRecordSummaryView::commonNameZh,
                CatchRecordSummaryView::hasPhoto)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(31L, "cyprinus-carpio", "鲤", false),
                        org.assertj.core.groups.Tuple.tuple(30L, "channa-argus", "乌鳢", true));
        assertThat(page).extracting(
                CatchRecordPageView::page,
                CatchRecordPageView::size,
                CatchRecordPageView::totalItems,
                CatchRecordPageView::totalPages)
                .containsExactly(1, 20, 22L, 2);
    }

    @Test
    void rejectsANegativePageWithTheStableCatchErrorCode() {
        assertThatThrownBy(() -> service.list("angler@example.com", -1))
                .isInstanceOfSatisfying(InvalidCatchRecordQueryException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_CATCH_RECORD"));
    }

    @Test
    void usesTheShanghaiDateAtTheUtcDayBoundaryForFutureDateValidation() {
        CatchRecordApplicationService boundaryService = new DefaultCatchRecordApplicationService(
                profiles, catalog, repository, SHANGHAI_BOUNDARY_CLOCK);

        boundaryService.create("angler@example.com",
                command("channa-argus", LocalDate.parse("2026-08-20"), "城郊水库"));

        assertThat(repository.saved.details().caughtOn()).isEqualTo(LocalDate.parse("2026-08-20"));
        assertThatThrownBy(() -> boundaryService.create("angler@example.com",
                command("channa-argus", LocalDate.parse("2026-08-21"), "城郊水库")))
                .isInstanceOfSatisfying(InvalidCatchRecordException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_CATCH_RECORD"));
        assertThat(repository.saved.details().caughtOn()).isEqualTo(LocalDate.parse("2026-08-20"));
    }

    @Test
    void updatesOnlyEditableFieldsWhilePreservingThePhotoReference() {
        CatchRecord existing = record(31L, 1L, LocalDate.parse("2026-08-19"), "private/key");
        repository.ownedRecord = Optional.of(existing);

        CatchRecordDetailView updated = service.update("angler@example.com", 31L,
                command("cyprinus-carpio", LocalDate.parse("2026-08-20"), " 新钓点 "));

        assertThat(repository.lastOwnedUserId).isEqualTo(41L);
        assertThat(repository.saved).extracting(
                CatchRecord::id,
                CatchRecord::userId,
                CatchRecord::photoObjectKey,
                CatchRecord::createdAt,
                CatchRecord::updatedAt)
                .containsExactly(31L, 41L, "private/key", existing.createdAt(),
                        Instant.parse("2026-08-20T02:00:00Z"));
        assertThat(repository.saved.details()).isEqualTo(new CatchRecordDetails(
                2L, LocalDate.parse("2026-08-20"), "新钓点",
                new BigDecimal("42.5"), new BigDecimal("1350"), "路亚", "傍晚近岸中鱼"));
        assertThat(updated).extracting(
                CatchRecordDetailView::fishSlug,
                CatchRecordDetailView::commonNameZh,
                CatchRecordDetailView::hasPhoto)
                .containsExactly("cyprinus-carpio", "鲤", true);
    }

    @Test
    void deletesOnlyTheAuthenticatedUsersRecordAndReportsMissingRecords() {
        repository.deleteResult = false;

        assertThatThrownBy(() -> service.delete("angler@example.com", 31L))
                .isInstanceOfSatisfying(CatchRecordNotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("CATCH_RECORD_NOT_FOUND"));
        assertThat(repository.deletedId).isEqualTo(31L);
        assertThat(repository.deletedUserId).isEqualTo(41L);
    }

    @Test
    void rejectsNonCanonicalFishSlugsAsCatchInputBeforeCatalogLookupOrSaving() {
        assertThatThrownBy(() -> service.create("angler@example.com",
                command("Channa_argus", LocalDate.parse("2026-08-20"), "城郊水库")))
                .isInstanceOfSatisfying(InvalidCatchRecordException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_CATCH_RECORD"));
        assertThat(catalog.referenceLookupCount).isZero();
        assertThat(repository.saved).isNull();
    }

    @Test
    void preservesFishNotFoundForACanonicalButMissingFishSlug() {
        assertThatThrownBy(() -> service.create("angler@example.com",
                command("missing-fish", LocalDate.parse("2026-08-20"), "城郊水库")))
                .isInstanceOfSatisfying(FishNotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("FISH_NOT_FOUND"));
        assertThat(repository.saved).isNull();
    }

    private static CatchRecordCommand command(String fishSlug, LocalDate caughtOn, String location) {
        return new CatchRecordCommand(fishSlug, caughtOn, location,
                new BigDecimal("42.5"), new BigDecimal("1350"), " 路亚 ", " 傍晚近岸中鱼 ");
    }

    private static CatchRecord record(Long id, long fishId, LocalDate caughtOn, String photoObjectKey) {
        return CatchRecord.restore(id, 41L, new CatchRecordDetails(
                        fishId, caughtOn, "城郊水库", new BigDecimal("42.5"),
                        new BigDecimal("1350"), "路亚", "傍晚近岸中鱼"),
                photoObjectKey, Instant.parse("2026-08-19T12:00:00Z"),
                Instant.parse("2026-08-19T12:00:00Z"));
    }

    private static final class RecordingProfileService implements ProfileApplicationService {
        private String requestedEmail;

        @Override
        public UserView currentUser(String normalizedEmail) {
            requestedEmail = normalizedEmail;
            return new UserView(41L, normalizedEmail, "angler", "USER");
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
                1L, summary("channa-argus", "乌鳢"),
                2L, summary("cyprinus-carpio", "鲤"));
        private List<Long> lastSummaryIds = List.of();
        private int summaryLookupCount;
        private int referenceLookupCount;

        @Override public FishPageView search(FishCatalogQuery query) { throw new UnsupportedOperationException(); }
        @Override public FishDetailView getBySlug(String slug) { throw new UnsupportedOperationException(); }

        @Override
        public FishReferenceView getReferenceBySlug(String slug) {
            referenceLookupCount++;
            if ("missing-fish".equals(slug)) {
                throw new FishNotFoundException(slug);
            }
            return references.get(slug);
        }

        @Override public List<FishReferenceView> getReferencesBySlugs(List<String> slugs) { throw new UnsupportedOperationException(); }

        @Override
        public List<FishSummaryView> getSummariesByIds(List<Long> ids) {
            summaryLookupCount++;
            lastSummaryIds = List.copyOf(ids);
            return ids.stream().map(summaries::get).toList();
        }

        @Override public FishFilterOptionsView getFilterOptions() { throw new UnsupportedOperationException(); }

        private static FishSummaryView summary(String slug, String commonNameZh) {
            return new FishSummaryView(slug, commonNameZh, "Latin", "Family", List.of(), List.of(),
                    "/images/" + slug + ".jpg", commonNameZh);
        }
    }

    private static final class RecordingCatchRecordRepository implements CatchRecordRepository {
        private CatchRecord saved;
        private Optional<CatchRecord> ownedRecord = Optional.of(record(31L, 1L,
                LocalDate.parse("2026-08-20"), null));
        private CatchRecordPage page = new CatchRecordPage(List.of(), 0, 20, 0, 0);
        private long lastOwnedUserId;
        private long lastListUserId;
        private int lastPage;
        private int lastSize;
        private long deletedId;
        private long deletedUserId;
        private boolean deleteResult = true;

        @Override
        public CatchRecord save(CatchRecord record) {
            saved = record.id() == null
                    ? CatchRecord.restore(31L, record.userId(), record.details(), record.photoObjectKey(),
                            record.createdAt(), record.updatedAt())
                    : record;
            return saved;
        }

        @Override
        public Optional<CatchRecord> findByIdAndUserId(long id, long userId) {
            lastOwnedUserId = userId;
            return ownedRecord;
        }

        @Override
        public CatchRecordPage findByUserId(long userId, int page, int size) {
            lastListUserId = userId;
            lastPage = page;
            lastSize = size;
            return this.page;
        }

        @Override
        public boolean deleteByIdAndUserId(long id, long userId) {
            deletedId = id;
            deletedUserId = userId;
            return deleteResult;
        }
    }
}
