package com.fishbook.catalog.domain;

import java.util.Optional;

public interface FishManagementRepository {
    FishPage searchManaged(FishManagementSearchCriteria criteria);

    Optional<FishSpecies> findManagedById(long id);

    FishSpecies save(FishSpecies fish);
}
