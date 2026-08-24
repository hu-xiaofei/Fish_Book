package com.fishbook.catchlog.application;

import java.util.Objects;

public record CatchPhotoView(byte[] content, String contentType) {
    public CatchPhotoView {
        content = Objects.requireNonNull(content, "content must not be null").clone();
        contentType = Objects.requireNonNull(contentType, "contentType must not be null");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
