package com.fishbook.administration.application;

import com.fishbook.catalog.domain.PublicationStatus;
import java.util.Locale;

public record AdminFishQuery(String query, PublicationStatus status, int page) {

    public static AdminFishQuery from(String rawQuery, String rawStatus, String rawPage, String rawSize) {
        if (rawSize != null) {
            throw new InvalidAdminFishQueryException("size is fixed at 20");
        }
        return new AdminFishQuery(normalizeQuery(rawQuery), parseStatus(rawStatus), parsePage(rawPage));
    }

    private static String normalizeQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.strip();
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new InvalidAdminFishQueryException("q exceeds 100 characters");
        }
        return normalized;
    }

    private static PublicationStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PublicationStatus.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidAdminFishQueryException("status is unsupported");
        }
    }

    private static int parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int page = Integer.parseInt(raw);
            if (page < 0) {
                throw new NumberFormatException("negative page");
            }
            return page;
        } catch (NumberFormatException exception) {
            throw new InvalidAdminFishQueryException("page must be a non-negative integer");
        }
    }
}
