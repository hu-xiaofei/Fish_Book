package com.fishbook.catalog.domain;

public final class FishNotFoundException extends RuntimeException {

    public FishNotFoundException(String slug) {
        super("Fish was not found: " + slug);
    }

    public String code() {
        return "FISH_NOT_FOUND";
    }
}
