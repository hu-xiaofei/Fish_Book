package com.fishbook.catchlog.application;

public class CatchPhotoConflictException extends RuntimeException {
    public CatchPhotoConflictException() {
        super("照片或记录已被修改，请刷新后重新确认操作");
    }

    public String code() {
        return "CATCH_PHOTO_CONFLICT";
    }
}
