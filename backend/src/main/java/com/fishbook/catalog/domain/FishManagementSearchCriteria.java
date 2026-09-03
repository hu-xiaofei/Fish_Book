package com.fishbook.catalog.domain;

public record FishManagementSearchCriteria(
        String query,
        PublicationStatus status,
        int page,
        int size) {}
