package com.fishbook.catchlog.application;

import com.fishbook.catalog.application.FishCatalogQueryService;
import com.fishbook.catalog.application.FishReferenceView;
import com.fishbook.catalog.application.FishSummaryView;
import com.fishbook.catchlog.domain.CatchRecord;
import com.fishbook.catchlog.domain.CatchRecordDetails;
import com.fishbook.catchlog.domain.CatchRecordNotFoundException;
import com.fishbook.catchlog.domain.CatchRecordPage;
import com.fishbook.catchlog.domain.CatchRecordRepository;
import com.fishbook.catchlog.domain.InvalidCatchRecordException;
import com.fishbook.identity.application.ProfileApplicationService;
import com.fishbook.identity.application.UserView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultCatchRecordApplicationService implements CatchRecordApplicationService {

    private static final int PAGE_SIZE = 20;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String CANONICAL_SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

    private final ProfileApplicationService profileApplicationService;
    private final FishCatalogQueryService fishCatalogQueryService;
    private final CatchRecordRepository catchRecordRepository;
    private final Clock clock;

    public DefaultCatchRecordApplicationService(
            ProfileApplicationService profileApplicationService,
            FishCatalogQueryService fishCatalogQueryService,
            CatchRecordRepository catchRecordRepository,
            Clock clock) {
        this.profileApplicationService = Objects.requireNonNull(profileApplicationService);
        this.fishCatalogQueryService = Objects.requireNonNull(fishCatalogQueryService);
        this.catchRecordRepository = Objects.requireNonNull(catchRecordRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public CatchRecordDetailView create(String authenticatedEmail, CatchRecordCommand command) {
        validateCommandSlug(command);
        UserView user = currentUser(authenticatedEmail);
        FishReferenceView fish = fishCatalogQueryService.getReferenceBySlug(command.fishSlug());
        Instant now = clock.instant();
        CatchRecordDetails details = toDetails(command, fish.id(), today());
        CatchRecord saved = catchRecordRepository.save(CatchRecord.create(user.id(), details, now));
        return toDetailView(saved, summaryFor(saved.details().fishId()));
    }

    @Override
    public CatchRecordPageView list(String authenticatedEmail, int page) {
        if (page < 0) {
            throw new InvalidCatchRecordQueryException("page must be a non-negative integer");
        }
        UserView user = currentUser(authenticatedEmail);
        CatchRecordPage records = catchRecordRepository.findByUserId(user.id(), page, PAGE_SIZE);
        List<Long> fishIds = records.items().stream().map(record -> record.details().fishId()).toList();
        Map<Long, FishSummaryView> summariesById = summariesById(fishIds);
        List<CatchRecordSummaryView> items = records.items().stream()
                .map(record -> toSummaryView(record, requiredSummary(summariesById, record.details().fishId())))
                .toList();
        return new CatchRecordPageView(
                items, records.page(), records.size(), records.totalItems(), records.totalPages());
    }

    @Override
    public CatchRecordDetailView get(String authenticatedEmail, long id) {
        UserView user = currentUser(authenticatedEmail);
        CatchRecord record = ownedRecord(id, user.id());
        return toDetailView(record, summaryFor(record.details().fishId()));
    }

    @Override
    @Transactional
    public CatchRecordDetailView update(String authenticatedEmail, long id, CatchRecordCommand command) {
        validateCommandSlug(command);
        UserView user = currentUser(authenticatedEmail);
        CatchRecord existing = ownedRecord(id, user.id());
        FishReferenceView fish = fishCatalogQueryService.getReferenceBySlug(command.fishSlug());
        CatchRecordDetails details = toDetails(command, fish.id(), today());
        CatchRecord saved = catchRecordRepository.save(existing.update(details, clock.instant()));
        return toDetailView(saved, summaryFor(saved.details().fishId()));
    }

    @Override
    @Transactional
    public void delete(String authenticatedEmail, long id) {
        UserView user = currentUser(authenticatedEmail);
        if (!catchRecordRepository.deleteByIdAndUserId(id, user.id())) {
            throw new CatchRecordNotFoundException(id);
        }
    }

    private UserView currentUser(String authenticatedEmail) {
        return profileApplicationService.currentUser(authenticatedEmail);
    }

    private CatchRecord ownedRecord(long id, long userId) {
        return catchRecordRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CatchRecordNotFoundException(id));
    }

    private CatchRecordDetails toDetails(CatchRecordCommand command, long fishId, LocalDate today) {
        return CatchRecordDetails.validated(
                fishId, command.caughtOn(), command.location(), command.lengthCm(), command.weightG(),
                command.method(), command.notes(), today);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SHANGHAI));
    }

    private void validateCommandSlug(CatchRecordCommand command) {
        if (command == null || command.fishSlug() == null
                || !command.fishSlug().matches(CANONICAL_SLUG_PATTERN)) {
            throw new InvalidCatchRecordException("fish slug must be canonical and nonblank");
        }
    }

    private FishSummaryView summaryFor(long fishId) {
        List<FishSummaryView> summaries = fishCatalogQueryService.getSummariesByIds(List.of(fishId));
        if (summaries.size() != 1 || summaries.getFirst() == null) {
            throw new IllegalStateException("catch record fish must exist");
        }
        return summaries.getFirst();
    }

    private Map<Long, FishSummaryView> summariesById(List<Long> fishIds) {
        List<FishSummaryView> summaries = fishCatalogQueryService.getSummariesByIds(fishIds);
        if (summaries.size() != fishIds.size()) {
            throw new IllegalStateException("catch record fish summaries must match records");
        }
        Map<Long, FishSummaryView> summariesById = new HashMap<>();
        for (int index = 0; index < fishIds.size(); index++) {
            FishSummaryView summary = summaries.get(index);
            if (summary == null) {
                throw new IllegalStateException("catch record fish must exist");
            }
            summariesById.put(fishIds.get(index), summary);
        }
        return summariesById;
    }

    private FishSummaryView requiredSummary(Map<Long, FishSummaryView> summariesById, long fishId) {
        FishSummaryView summary = summariesById.get(fishId);
        if (summary == null) {
            throw new IllegalStateException("catch record fish must exist");
        }
        return summary;
    }

    private CatchRecordSummaryView toSummaryView(CatchRecord record, FishSummaryView fish) {
        CatchRecordDetails details = record.details();
        return new CatchRecordSummaryView(
                requiredId(record), fish.slug(), fish.commonNameZh(), details.caughtOn(), details.location(),
                details.lengthCm(), details.weightG(), details.method(), record.photoObjectKey() != null,
                record.createdAt(), record.updatedAt());
    }

    private CatchRecordDetailView toDetailView(CatchRecord record, FishSummaryView fish) {
        CatchRecordDetails details = record.details();
        return new CatchRecordDetailView(
                requiredId(record), fish.slug(), fish.commonNameZh(), details.caughtOn(), details.location(),
                details.lengthCm(), details.weightG(), details.method(), details.notes(),
                record.photoObjectKey() != null, record.createdAt(), record.updatedAt());
    }

    private long requiredId(CatchRecord record) {
        if (record.id() == null) {
            throw new IllegalStateException("saved catch record must have an ID");
        }
        return record.id();
    }
}
