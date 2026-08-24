package com.fishbook.media.application;

public class InvalidCatchPhotoException extends RuntimeException {
    private static final String CODE = "INVALID_CATCH_PHOTO";

    public InvalidCatchPhotoException() {
        super("照片必须是 10 MB 以内的 JPEG、PNG 或 WebP 文件");
    }

    public String code() {
        return CODE;
    }
}
