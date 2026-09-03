package com.fishbook.catalog.domain;

public final class InvalidFishSpeciesException extends IllegalArgumentException {
    public InvalidFishSpeciesException(String message) {
        super(message);
    }

    public String code() {
        return "VALIDATION_FAILED";
    }
}
