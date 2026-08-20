package com.fishbook.catchlog.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record CatchRecordDetails(
        long fishId,
        LocalDate caughtOn,
        String location,
        BigDecimal lengthCm,
        BigDecimal weightG,
        String method,
        String notes) {

    private static final LocalDate MIN_CAUGHT_ON = LocalDate.of(1000, 1, 1);

    public CatchRecordDetails {
        if (fishId <= 0 || caughtOn == null || caughtOn.isBefore(MIN_CAUGHT_ON)) {
            throw new InvalidCatchRecordException("fish and caught date must be valid");
        }
        location = requiredText(location, 200, "location");
        method = optionalText(method, 100, "method");
        notes = optionalText(notes, 5_000, "notes");
        lengthCm = measurement(lengthCm, new BigDecimal("999999.99"), "lengthCm");
        weightG = measurement(weightG, new BigDecimal("99999999.99"), "weightG");
    }

    public static CatchRecordDetails validated(
            long fishId, LocalDate caughtOn, String location,
            BigDecimal lengthCm, BigDecimal weightG,
            String method, String notes, LocalDate today) {
        Objects.requireNonNull(today, "today must not be null");
        if (fishId <= 0 || caughtOn == null
                || caughtOn.isBefore(MIN_CAUGHT_ON) || caughtOn.isAfter(today)) {
            throw new InvalidCatchRecordException("fish and caught date must be valid");
        }
        String normalizedLocation = requiredText(location, 200, "location");
        String normalizedMethod = optionalText(method, 100, "method");
        String normalizedNotes = optionalText(notes, 5_000, "notes");
        BigDecimal normalizedLength = measurement(
                lengthCm, new BigDecimal("999999.99"), "lengthCm");
        BigDecimal normalizedWeight = measurement(
                weightG, new BigDecimal("99999999.99"), "weightG");
        return new CatchRecordDetails(
                fishId, caughtOn, normalizedLocation, normalizedLength,
                normalizedWeight, normalizedMethod, normalizedNotes);
    }

    private static String requiredText(String value, int max, String field) {
        if (value == null || value.strip().isEmpty() || value.strip().length() > max) {
            throw new InvalidCatchRecordException(field + " is invalid");
        }
        return value.strip();
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null || value.strip().isEmpty()) {
            return null;
        }
        if (value.strip().length() > max) {
            throw new InvalidCatchRecordException(field + " is invalid");
        }
        return value.strip();
    }

    private static BigDecimal measurement(BigDecimal value, BigDecimal max, String field) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0 || value.compareTo(max) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw new InvalidCatchRecordException(field + " is invalid");
        }
        return value;
    }
}
