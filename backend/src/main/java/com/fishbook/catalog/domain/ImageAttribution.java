package com.fishbook.catalog.domain;

public record ImageAttribution(
        String path,
        String altText,
        String sourceUrl,
        String author,
        String licenseName,
        String licenseUrl) {
    public ImageAttribution {
        requireText(path, "path");
        requireText(altText, "altText");
        requireText(sourceUrl, "sourceUrl");
        requireText(author, "author");
        requireText(licenseName, "licenseName");
        requireText(licenseUrl, "licenseUrl");
        if (!path.matches("/images/fish/[a-z0-9-]+\\.(jpg|jpeg|png|webp)")) {
            throw new IllegalArgumentException("path must be a local fish image");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
