package com.fishbook.catchlog.web;

import com.fishbook.catchlog.application.InvalidPhotoVersionException;
import com.fishbook.catchlog.application.PhotoVersionRequiredException;

public final class PhotoRevision {
    private PhotoRevision() {}
    public static long parse(String value) {
        if (value == null) throw new PhotoVersionRequiredException();
        if (!value.matches("\"[0-9]+\"")) throw new InvalidPhotoVersionException();
        try { return Long.parseLong(value.substring(1, value.length() - 1)); }
        catch (NumberFormatException failure) { throw new InvalidPhotoVersionException(); }
    }
}
