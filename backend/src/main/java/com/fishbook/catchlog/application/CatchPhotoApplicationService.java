package com.fishbook.catchlog.application;

public interface CatchPhotoApplicationService {
    void put(String authenticatedEmail, long recordId, byte[] content, String declaredContentType);

    CatchPhotoView get(String authenticatedEmail, long recordId);

    void remove(String authenticatedEmail, long recordId);
}
