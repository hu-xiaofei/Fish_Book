package com.fishbook.catalog.domain;

import java.util.List;
import java.util.Optional;

public interface FishRepository {
    FishPage searchPublished(FishSearchCriteria criteria);

    Optional<FishSpecies> findPublishedBySlug(String slug);

    Optional<FishSpecies> findAnyBySlug(String slug);

    List<FishSpecies> findAllByIds(List<Long> ids);

    List<FishSpecies> findAllPublishedBySlugs(List<String> slugs);

    List<String> findPublishedAvailableFamilies();

    List<HabitatType> findPublishedAvailableHabitats();
}
