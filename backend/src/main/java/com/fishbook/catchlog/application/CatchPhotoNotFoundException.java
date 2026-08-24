package com.fishbook.catchlog.application;

public class CatchPhotoNotFoundException extends RuntimeException {
    private static final String CODE = "CATCH_PHOTO_NOT_FOUND";

    public CatchPhotoNotFoundException() {
        super("Catch photo was not found");
    }

    public String code() {
        return CODE;
    }
}
