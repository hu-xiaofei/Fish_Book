package com.fishbook.catalog.domain;

import java.util.List;
import java.util.Optional;

public interface FishRepository {
    FishPage search(FishSearchCriteria criteria);

    Optional<FishSpecies> findBySlug(String slug);

    List<String> findAvailableFamilies();
}
