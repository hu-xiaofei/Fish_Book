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
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        validateSlug(slug);
        FishSpecies fish = repository.findBySlug(slug)
                .orElseThrow(() -> new FishNotFoundException(slug));
        return toDetail(fish);
    }

    @Override
    @Transactional(readOnly = true)
    public FishReferenceView getReferenceBySlug(String slug) {
        validateSlug(slug);
        FishSpecies fish = repository.findBySlug(slug)
                .orElseThrow(() -> new FishNotFoundException(slug));
        return toReference(fish);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FishReferenceView> getReferencesBySlugs(List<String> slugs) {
        validateSlugs(slugs);
        Map<String, FishSpecies> fishBySlug = repository.findAllBySlugs(slugs).stream()
                .collect(Collectors.toMap(FishSpecies::slug, Function.identity()));
        return slugs.stream()
                .map(slug -> {
                    FishSpecies fish = fishBySlug.get(slug);
                    if (fish == null) {
                        throw new FishNotFoundException(slug);
                    }
                    return toReference(fish);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FishSummaryView> getSummariesByIds(List<Long> ids) {
        validateIds(ids);
        List<Long> distinctIds = ids.stream().distinct().toList();
        Map<Long, FishSpecies> fishById = repository.findAllByIds(distinctIds).stream()
                .collect(Collectors.toMap(FishSpecies::id, Function.identity()));
        return ids.stream()
                .map(id -> fishById.get(id))
                .map(fish -> {
                    if (fish == null) {
                        throw new IllegalStateException("fish ID must exist");
                    }
                    return toSummary(fish);
                })
                .toList();
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

    private FishReferenceView toReference(FishSpecies fish) {
        return new FishReferenceView(fish.id(), fish.slug());
    }

    private void validateSlug(String slug) {
        if (slug == null || !slug.matches(CANONICAL_SLUG_PATTERN)) {
            throw new InvalidCatalogQueryException("slug must be canonical and nonblank");
        }
    }

    private void validateSlugs(List<String> slugs) {
        if (slugs == null || slugs.stream().anyMatch(Objects::isNull)) {
            throw new InvalidCatalogQueryException("slugs must not contain null values");
        }
        slugs.forEach(this::validateSlug);
        if (slugs.size() != slugs.stream().distinct().count()) {
            throw new InvalidCatalogQueryException("slugs must not contain duplicates");
        }
    }

    private void validateIds(List<Long> ids) {
        if (ids == null || ids.stream().anyMatch(Objects::isNull)) {
            throw new InvalidCatalogQueryException("ids must not contain null values");
        }
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
