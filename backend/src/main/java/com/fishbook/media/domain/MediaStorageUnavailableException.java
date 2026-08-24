package com.fishbook.media.domain;

public class MediaStorageUnavailableException extends RuntimeException {
    private static final String MESSAGE = "媒体存储暂时不可用";

    public MediaStorageUnavailableException() {
        super(MESSAGE);
    }

    public MediaStorageUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
