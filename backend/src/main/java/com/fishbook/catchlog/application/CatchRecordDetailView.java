package com.fishbook.catchlog.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CatchRecordDetailView(
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
}
