package com.fishbook.catchlog.application;

import java.util.List;

public record CatchRecordPageView(
        List<CatchRecordSummaryView> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
    public CatchRecordPageView {
        items = List.copyOf(items);
    }
}
