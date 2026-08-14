package com.fishbook.favorites.application;

public final class InvalidFavoriteQueryException extends RuntimeException {

    public InvalidFavoriteQueryException(String message) {
        super(message);
    }

    public String code() {
        return "INVALID_FAVORITE_QUERY";
    }
}
