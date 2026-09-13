package com.fishbook.catchlog.application;

public interface CatchPhotoApplicationService {
    void put(String authenticatedEmail, long recordId, byte[] content, String declaredContentType, long expectedVersion);

    CatchPhotoView get(String authenticatedEmail, long recordId);

    void remove(String authenticatedEmail, long recordId, long expectedVersion);

    CatchPhotoView getForAdmin(String authenticatedEmail, long recordId);
    void putForAdmin(String authenticatedEmail, long recordId, byte[] content, String declaredContentType, long expectedVersion);
    void removeForAdmin(String authenticatedEmail, long recordId, long expectedVersion);
}
