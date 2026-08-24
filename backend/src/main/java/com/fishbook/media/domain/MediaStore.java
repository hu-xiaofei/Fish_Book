package com.fishbook.media.domain;

public interface MediaStore {
    void put(String objectKey, byte[] content, String contentType);

    StoredMedia get(String objectKey);

    void delete(String objectKey);
}
