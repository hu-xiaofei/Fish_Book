package com.fishbook.catchlog.web.dto;

import com.fishbook.catchlog.application.CatchRecordPageView;
import java.util.List;

public record CatchRecordPageResponse(
        List<CatchRecordSummaryResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public CatchRecordPageResponse {
        items = List.copyOf(items);
    }

    public static CatchRecordPageResponse from(CatchRecordPageView view) {
        return new CatchRecordPageResponse(
                view.items().stream().map(CatchRecordSummaryResponse::from).toList(),
                view.page(), view.size(), view.totalItems(), view.totalPages());
    }
}
