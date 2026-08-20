package com.fishbook.catchlog.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CatchRecordCommand(
        String fishSlug,
        LocalDate caughtOn,
        String location,
        BigDecimal lengthCm,
        BigDecimal weightG,
        String method,
        String notes) {
}
