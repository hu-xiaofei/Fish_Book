package com.fishbook.catalog.domain;

import java.net.URI;

public record ImageAttribution(
        String path,
        String altText,
        String sourceUrl,
        String author,
        String licenseName,
        String licenseUrl) {
    public ImageAttribution {
        path = required(path, 255, "path");
        altText = required(altText, 255, "altText");
        sourceUrl = required(sourceUrl, 1000, "sourceUrl");
        author = required(author, 255, "author");
        licenseName = required(licenseName, 100, "licenseName");
        licenseUrl = required(licenseUrl, 1000, "licenseUrl");
        if (!path.matches("/images/fish/[a-z0-9-]+\\.(jpg|jpeg|png|webp)")) {
            throw new InvalidFishSpeciesException("path must be a local fish image");
        }
        requireHttpUrl(sourceUrl, "sourceUrl");
        requireHttpUrl(licenseUrl, "licenseUrl");
    }

    private static String required(String value, int maxCodePoints, String field) {
        if (value == null) {
            throw new InvalidFishSpeciesException(field + " must not be null");
        }
        String normalized = value.strip();
        if (normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new InvalidFishSpeciesException(field + " has an invalid length");
        }
        return normalized;
    }

    private static void requireHttpUrl(String value, String field) {
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new InvalidFishSpeciesException(field + " must be an absolute http or https URL");
            }
        } catch (IllegalArgumentException exception) {
            if (exception instanceof InvalidFishSpeciesException invalid) {
                throw invalid;
            }
            throw new InvalidFishSpeciesException(field + " must be an absolute http or https URL");
        }
    }
}
