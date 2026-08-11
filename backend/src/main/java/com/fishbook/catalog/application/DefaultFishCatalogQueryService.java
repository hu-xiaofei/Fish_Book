package com.fishbook.catalog.application;

import com.fishbook.catalog.domain.FishNotFoundException;
import com.fishbook.catalog.domain.FishPage;
import com.fishbook.catalog.domain.FishRepository;
import com.fishbook.catalog.domain.FishSearchCriteria;
import com.fishbook.catalog.domain.FishSpecies;
import com.fishbook.catalog.domain.HabitatType;
import com.fishbook.catalog.domain.ImageAttribution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class DefaultFishCatalogQueryService implements FishCatalogQueryService {

    private static final int PAGE_SIZE = 12;
    private static final String CANONICAL_SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

    private final FishRepository repository;

    public DefaultFishCatalogQueryService(FishRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    @Transactional(readOnly = true)
    public FishPageView search(FishCatalogQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        FishPage page = repository.search(new FishSearchCriteria(
                query.query(), query.family(), query.habitat(), query.page(), PAGE_SIZE));
        return new FishPageView(
                page.items().stream().map(this::toSummary).toList(),
                page.page(), page.size(), page.totalItems(), page.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public FishDetailView getBySlug(String slug) {
        if (slug == null || !slug.matches(CANONICAL_SLUG_PATTERN)) {
            throw new InvalidCatalogQueryException("slug must be canonical and nonblank");
        }
        FishSpecies fish = repository.findBySlug(slug)
                .orElseThrow(() -> new FishNotFoundException(slug));
        return toDetail(fish);
    }

    @Override
    @Transactional(readOnly = true)
    public FishFilterOptionsView getFilterOptions() {
        return new FishFilterOptionsView(
                repository.findAvailableFamilies(),
                List.of(HabitatType.values()).stream().map(this::toHabitatOption).toList());
    }

    private FishSummaryView toSummary(FishSpecies fish) {
        return new FishSummaryView(
                fish.slug(),
                fish.commonNameZh(),
                fish.scientificName(),
                fish.familyNameZh(),
                fish.aliases(),
                fish.habitats().stream().map(this::toHabitatOption).toList(),
                fish.image().path(),
                fish.image().altText());
    }

    private FishDetailView toDetail(FishSpecies fish) {
        return new FishDetailView(
                fish.slug(),
                fish.commonNameZh(),
                fish.scientificName(),
                fish.familyNameZh(),
                fish.familyScientificName(),
                fish.genusNameZh(),
                fish.genusScientificName(),
                fish.aliases(),
                fish.habitats().stream().map(this::toHabitatOption).toList(),
                fish.appearance(),
                fish.sizeDescription(),
                fish.habitatDescription(),
                fish.distribution(),
                fish.description(),
                toImageAttribution(fish.image()));
    }

    private HabitatOptionView toHabitatOption(HabitatType habitat) {
        return new HabitatOptionView(habitat.name(), habitat.labelZh());
    }

    private ImageAttributionView toImageAttribution(ImageAttribution image) {
        return new ImageAttributionView(
                image.path(),
                image.altText(),
                image.sourceUrl(),
                image.author(),
                image.licenseName(),
                image.licenseUrl());
    }
}
