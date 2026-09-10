package com.fishbook.administration.application;

import com.fishbook.catalog.domain.FishManagementRepository;
import com.fishbook.catalog.domain.FishManagementSearchCriteria;
import com.fishbook.catalog.domain.FishNotFoundException;
import com.fishbook.catalog.domain.FishPage;
import com.fishbook.catalog.domain.FishSpecies;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultFishAdministrationService implements FishAdministrationService {

    private static final int PAGE_SIZE = 20;

    private final FishManagementRepository repository;
    private final Clock clock;

    public DefaultFishAdministrationService(FishManagementRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminFishPageView search(AdminFishQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        FishPage page = repository.searchManaged(new FishManagementSearchCriteria(
                query.query(), query.status(), query.page(), PAGE_SIZE));
        return new AdminFishPageView(
                page.items().stream().map(this::toSummary).toList(),
                page.page(), page.size(), page.totalItems(), page.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminFishDetailView get(long id) {
        return toDetail(findFish(id));
    }

    @Override
    @Transactional
    public AdminFishDetailView create(CreateFishCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        FishSpecies created = FishSpecies.createDraft(command.slug(), command.content().toDomain(), clock.instant());
        return toDetail(repository.save(created));
    }

    @Override
    @Transactional
    public AdminFishDetailView update(long id, FishContentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return toDetail(repository.save(findFish(id).edit(command.toDomain(), clock.instant())));
    }

    @Override
    @Transactional
    public AdminFishDetailView publish(long id) {
        return toDetail(repository.save(findFish(id).publish(clock.instant())));
    }

    @Override
    @Transactional
    public AdminFishDetailView unpublish(long id) {
        return toDetail(repository.save(findFish(id).unpublish(clock.instant())));
    }

    private FishSpecies findFish(long id) {
        return repository.findManagedById(id)
                .orElseThrow(() -> new FishNotFoundException(String.valueOf(id)));
    }

    private AdminFishSummaryView toSummary(FishSpecies fish) {
        return new AdminFishSummaryView(
                fish.id(), fish.slug(), fish.commonNameZh(), fish.scientificName(),
                fish.status(), fish.updatedAt());
    }

    private AdminFishDetailView toDetail(FishSpecies fish) {
        return new AdminFishDetailView(
                fish.id(), fish.slug(), fish.commonNameZh(), fish.scientificName(),
                fish.familyNameZh(), fish.familyScientificName(), fish.genusNameZh(),
                fish.genusScientificName(), fish.aliases(), fish.habitats(), fish.appearance(),
                fish.sizeDescription(), fish.habitatDescription(), fish.distribution(), fish.description(),
                fish.image().path(), fish.image().altText(), fish.image().sourceUrl(), fish.image().author(),
                fish.image().licenseName(), fish.image().licenseUrl(), fish.displayOrder(), fish.status(),
                fish.publishedAt(), fish.createdAt(), fish.updatedAt());
    }
}
