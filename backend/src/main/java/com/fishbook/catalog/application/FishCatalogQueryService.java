package com.fishbook.catalog.application;

import java.util.List;

public interface FishCatalogQueryService {

    FishPageView search(FishCatalogQuery query);

    FishDetailView getBySlug(String slug);

    FishReferenceView getReferenceBySlug(String slug);

    List<FishReferenceView> getReferencesBySlugs(List<String> slugs);

    List<FishSummaryView> getSummariesByIds(List<Long> ids);

    FishFilterOptionsView getFilterOptions();
}
