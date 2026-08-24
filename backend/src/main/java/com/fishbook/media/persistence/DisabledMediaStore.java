package com.fishbook.media.persistence;

import com.fishbook.media.domain.MediaStorageUnavailableException;
import com.fishbook.media.domain.MediaStore;
import com.fishbook.media.domain.StoredMedia;

public final class DisabledMediaStore implements MediaStore {
    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        throw new MediaStorageUnavailableException();
    }

    @Override
    public StoredMedia get(String objectKey) {
        throw new MediaStorageUnavailableException();
    }

    @Override
    public void delete(String objectKey) {
        throw new MediaStorageUnavailableException();
    }
}
