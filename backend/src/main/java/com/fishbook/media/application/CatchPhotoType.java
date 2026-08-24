package com.fishbook.media.application;

public enum CatchPhotoType {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    private final String contentType;

    CatchPhotoType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }
}
