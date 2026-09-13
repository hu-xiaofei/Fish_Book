package com.fishbook.catchlog.application;

public class InvalidPhotoVersionException extends RuntimeException {
    public String code() { return "INVALID_PHOTO_VERSION"; }
}
