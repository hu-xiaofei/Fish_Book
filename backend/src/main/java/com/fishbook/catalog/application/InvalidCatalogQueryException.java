package com.fishbook.catalog.application;

public final class InvalidCatalogQueryException extends RuntimeException {

    public InvalidCatalogQueryException(String message) {
        super(message);
    }

    public String code() {
        return "INVALID_CATALOG_QUERY";
    }
}
