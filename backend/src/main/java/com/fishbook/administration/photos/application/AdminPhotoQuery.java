package com.fishbook.administration.photos.application;

public record AdminPhotoQuery(Long userId, int page, int size) {
    public AdminPhotoQuery {
        if (userId != null && userId <= 0 || page < 0 || size < 1 || size > 50)
            throw new InvalidAdminPhotoQueryException();
    }
    public static AdminPhotoQuery from(String userId, String page, String size) {
        try {
            return new AdminPhotoQuery(userId == null ? null : positiveId(userId),
                    page == null ? 0 : Integer.parseInt(decimal(page)),
                    size == null ? 20 : Integer.parseInt(decimal(size)));
        } catch (NumberFormatException failure) { throw new InvalidAdminPhotoQueryException(); }
    }
    public static long positiveId(String value) {
        try {
            long id = Long.parseLong(decimal(value));
            if (id <= 0) throw new InvalidAdminPhotoQueryException();
            return id;
        } catch (NumberFormatException failure) { throw new InvalidAdminPhotoQueryException(); }
    }
    private static String decimal(String value) {
        if (value == null || !value.matches("[0-9]+")) throw new InvalidAdminPhotoQueryException();
        return value;
    }
}
