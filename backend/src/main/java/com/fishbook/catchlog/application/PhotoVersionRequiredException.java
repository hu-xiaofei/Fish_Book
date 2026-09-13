package com.fishbook.catchlog.application;

public class PhotoVersionRequiredException extends RuntimeException {
    public String code() { return "PHOTO_VERSION_REQUIRED"; }
}
