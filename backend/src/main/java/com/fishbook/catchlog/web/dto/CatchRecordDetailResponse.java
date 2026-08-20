package com.fishbook.catchlog.web.dto;

import com.fishbook.catchlog.application.CatchRecordDetailView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CatchRecordDetailResponse(
        long id,
        String fishSlug,
        String commonNameZh,
        LocalDate caughtOn,
        String location,
        BigDecimal lengthCm,
        BigDecimal weightG,
        String method,
        String notes,
        boolean hasPhoto,
        Instant createdAt,
        Instant updatedAt) {

    public static CatchRecordDetailResponse from(CatchRecordDetailView view) {
        return new CatchRecordDetailResponse(
                view.id(), view.fishSlug(), view.commonNameZh(), view.caughtOn(), view.location(),
                view.lengthCm(), view.weightG(), view.method(), view.notes(), view.hasPhoto(),
                view.createdAt(), view.updatedAt());
    }
}
