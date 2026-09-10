package com.fishbook.administration.application;

public final class InvalidAdminFishQueryException extends RuntimeException {

    public InvalidAdminFishQueryException(String message) {
        super(message);
    }

    public String code() {
        return "INVALID_CATALOG_QUERY";
    }
}
