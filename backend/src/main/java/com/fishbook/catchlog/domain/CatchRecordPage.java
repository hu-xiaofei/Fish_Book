package com.fishbook.catchlog.domain;

import java.util.List;

public record CatchRecordPage(
        List<CatchRecord> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
    public CatchRecordPage {
        items = List.copyOf(items);
    }
}
