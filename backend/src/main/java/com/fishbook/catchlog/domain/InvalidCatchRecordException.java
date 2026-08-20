package com.fishbook.catchlog.domain;

public final class InvalidCatchRecordException extends RuntimeException {

    public InvalidCatchRecordException(String message) {
        super(message);
    }

    public String code() {
        return "INVALID_CATCH_RECORD";
    }
}
