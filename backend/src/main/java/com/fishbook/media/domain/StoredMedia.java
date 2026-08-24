package com.fishbook.media.domain;

import java.util.Objects;

public record StoredMedia(byte[] content, String contentType) {
    public StoredMedia {
        content = Objects.requireNonNull(content, "content must not be null").clone();
        contentType = Objects.requireNonNull(contentType, "contentType must not be null");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
