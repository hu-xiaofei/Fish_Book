package com.fishbook.catchlog.web.dto;

import com.fishbook.catchlog.application.CatchRecordSummaryView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CatchRecordSummaryResponse(
        long id,
        String fishSlug,
        String commonNameZh,
        LocalDate caughtOn,
        String location,
        BigDecimal lengthCm,
        BigDecimal weightG,
        String method,
        boolean hasPhoto,
        Instant createdAt,
        Instant updatedAt) {

    public static CatchRecordSummaryResponse from(CatchRecordSummaryView view) {
        return new CatchRecordSummaryResponse(
                view.id(), view.fishSlug(), view.commonNameZh(), view.caughtOn(), view.location(),
                view.lengthCm(), view.weightG(), view.method(), view.hasPhoto(), view.createdAt(),
                view.updatedAt());
    }
}
