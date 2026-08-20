package com.fishbook.catchlog.domain;

public final class CatchRecordNotFoundException extends RuntimeException {

    public CatchRecordNotFoundException(long id) {
        super("Catch record was not found: " + id);
    }

    public String code() {
        return "CATCH_RECORD_NOT_FOUND";
    }
}
