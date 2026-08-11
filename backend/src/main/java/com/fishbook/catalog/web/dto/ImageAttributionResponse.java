package com.fishbook.catalog.web.dto;

import com.fishbook.catalog.application.ImageAttributionView;

public record ImageAttributionResponse(
        String path,
        String altText,
        String sourceUrl,
        String author,
        String licenseName,
        String licenseUrl) {

    public static ImageAttributionResponse from(ImageAttributionView view) {
        return new ImageAttributionResponse(
                view.path(),
                view.altText(),
                view.sourceUrl(),
                view.author(),
                view.licenseName(),
                view.licenseUrl());
    }
}
