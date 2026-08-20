package com.fishbook.catchlog.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CatchRecordSummaryView(
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
}
