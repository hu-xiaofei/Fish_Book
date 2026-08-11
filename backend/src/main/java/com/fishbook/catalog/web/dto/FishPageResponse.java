package com.fishbook.catalog.web.dto;

import com.fishbook.catalog.application.FishPageView;
import java.util.List;

public record FishPageResponse(
        List<FishSummaryResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public FishPageResponse {
        items = List.copyOf(items);
    }

    public static FishPageResponse from(FishPageView view) {
        return new FishPageResponse(
                view.items().stream().map(FishSummaryResponse::from).toList(),
                view.page(),
                view.size(),
                view.totalItems(),
                view.totalPages());
    }
}
