package com.fishbook.catchlog.application;

public final class InvalidCatchRecordQueryException extends RuntimeException {

    public InvalidCatchRecordQueryException(String message) {
        super(message);
    }

    public String code() {
        return "INVALID_CATCH_RECORD";
    }
}
