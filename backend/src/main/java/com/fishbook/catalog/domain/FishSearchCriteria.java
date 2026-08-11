package com.fishbook.catalog.domain;

public record FishSearchCriteria(
        String query,
        String family,
        HabitatType habitat,
        int page,
        int size) {}
