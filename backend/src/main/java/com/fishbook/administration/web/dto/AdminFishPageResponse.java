package com.fishbook.administration.web.dto;

import com.fishbook.administration.application.AdminFishPageView;
import java.util.List;

public record AdminFishPageResponse(
        List<AdminFishSummaryResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public AdminFishPageResponse {
        items = List.copyOf(items);
    }

    public static AdminFishPageResponse from(AdminFishPageView view) {
        return new AdminFishPageResponse(
                view.items().stream().map(AdminFishSummaryResponse::from).toList(),
                view.page(), view.size(), view.totalItems(), view.totalPages());
    }
}
