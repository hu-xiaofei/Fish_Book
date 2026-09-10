package com.fishbook.administration.web.dto;

import com.fishbook.administration.application.AdminFishSummaryView;
import com.fishbook.catalog.domain.PublicationStatus;
import java.time.Instant;

public record AdminFishSummaryResponse(
        long id,
        String slug,
        String commonNameZh,
        String scientificName,
        PublicationStatus status,
        Instant updatedAt) {

    public static AdminFishSummaryResponse from(AdminFishSummaryView view) {
        return new AdminFishSummaryResponse(
                view.id(), view.slug(), view.commonNameZh(), view.scientificName(),
                view.status(), view.updatedAt());
    }
}
