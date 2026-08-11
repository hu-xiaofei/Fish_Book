package com.fishbook.catalog.application;

public interface FishCatalogQueryService {

    FishPageView search(FishCatalogQuery query);

    FishDetailView getBySlug(String slug);

    FishFilterOptionsView getFilterOptions();
}
