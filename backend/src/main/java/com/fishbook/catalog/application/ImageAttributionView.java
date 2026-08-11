package com.fishbook.catalog.application;

public record ImageAttributionView(
        String path,
        String altText,
        String sourceUrl,
        String author,
        String licenseName,
        String licenseUrl) {}
