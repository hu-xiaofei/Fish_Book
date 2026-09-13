package com.fishbook.administration.photos.application;

public class InvalidAdminPhotoQueryException extends RuntimeException {
    public String code() { return "INVALID_ADMIN_PHOTO_QUERY"; }
}
