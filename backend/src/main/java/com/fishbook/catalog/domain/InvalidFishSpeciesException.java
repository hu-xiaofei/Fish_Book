package com.fishbook.catalog.domain;

public final class InvalidFishSpeciesException extends IllegalArgumentException {
    private final String field;

    public InvalidFishSpeciesException(String message) {
        this(null, message);
    }

    public InvalidFishSpeciesException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String code() {
        return "VALIDATION_FAILED";
    }

    public String field() {
        return field;
    }
}
