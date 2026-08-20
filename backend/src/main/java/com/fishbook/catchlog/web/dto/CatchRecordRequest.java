package com.fishbook.catchlog.web.dto;

import com.fishbook.catchlog.application.CatchRecordCommand;
import com.fishbook.catchlog.domain.InvalidCatchRecordException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record CatchRecordRequest(
        String fishSlug,
        String caughtOn,
        String location,
        BigDecimal lengthCm,
        BigDecimal weightG,
        String method,
        String notes) {

    public CatchRecordCommand toCommand() {
        try {
            return new CatchRecordCommand(
                    fishSlug, LocalDate.parse(caughtOn), location, lengthCm, weightG, method, notes);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new InvalidCatchRecordException("caughtOn must be an ISO-8601 date");
        }
    }
}
