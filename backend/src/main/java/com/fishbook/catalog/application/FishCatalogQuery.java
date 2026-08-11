package com.fishbook.catalog.application;

import com.fishbook.catalog.domain.HabitatType;

import java.util.Locale;

public record FishCatalogQuery(
        String query,
        String family,
        HabitatType habitat,
        int page) {

    public static FishCatalogQuery from(
            String rawQuery,
            String rawFamily,
            String rawHabitat,
            String rawPage,
            String rawSize) {
        if (rawSize != null) {
            throw new InvalidCatalogQueryException("size is fixed at 12");
        }
        String query = normalizeOptional(rawQuery, "q");
        String family = normalizeOptional(rawFamily, "family");
        HabitatType habitat = parseHabitat(rawHabitat);
        int page = parsePage(rawPage);
        return new FishCatalogQuery(query, family, habitat, page);
    }

    private static String normalizeOptional(String raw, String field) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new InvalidCatalogQueryException(field + " exceeds 100 characters");
        }
        return normalized;
    }

    private static HabitatType parseHabitat(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return HabitatType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidCatalogQueryException("habitat is unsupported");
        }
    }

    private static int parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed < 0) {
                throw new NumberFormatException("negative page");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new InvalidCatalogQueryException("page must be a non-negative integer");
        }
    }
}
