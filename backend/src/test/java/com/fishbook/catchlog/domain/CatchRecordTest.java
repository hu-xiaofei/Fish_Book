package com.fishbook.catchlog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CatchRecordTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-08-20");
    private static final Instant CREATED_AT = Instant.parse("2026-08-20T10:15:30Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-20T11:15:30Z");

    @Test
    void stripsRequiredAndOptionalTextAndTurnsBlankOptionalTextIntoNull() {
        // Bug caught: accepting raw whitespace would persist inconsistent text values.
        CatchRecordDetails details = CatchRecordDetails.validated(
                7L, LocalDate.parse("2026-08-20"), "  城郊水库  ",
                new BigDecimal("42.50"), new BigDecimal("1350.00"),
                "   ", "  傍晚近岸中鱼  ", LocalDate.parse("2026-08-20"));

        assertThat(details.location()).isEqualTo("城郊水库");
        assertThat(details.method()).isNull();
        assertThat(details.notes()).isEqualTo("傍晚近岸中鱼");
    }

    @Test
    void canonicalConstructorNormalizesStructuralTextWithoutBusinessDateValidation() {
        // Bug caught: callers that construct persisted details directly could bypass normalization.
        CatchRecordDetails details = new CatchRecordDetails(
                7L, LocalDate.parse("2099-01-01"), "  城郊水库  ",
                new BigDecimal("42.50"), new BigDecimal("1350.00"),
                "   ", "  傍晚近岸中鱼  ");

        assertThat(details.location()).isEqualTo("城郊水库");
        assertThat(details.method()).isNull();
        assertThat(details.notes()).isEqualTo("傍晚近岸中鱼");
    }

    @Test
    void rejectsFutureDatesNegativeMeasurementsAndColumnOverflow() {
        // Bug caught: invalid submission values could reach storage or appear as valid catch records.
        LocalDate today = LocalDate.parse("2026-08-20");

        assertThatThrownBy(() -> validDetails(today.plusDays(1), null, null, today))
                .isInstanceOf(InvalidCatchRecordException.class);
        assertThatThrownBy(() -> validDetails(today, new BigDecimal("-0.01"), null, today))
                .isInstanceOf(InvalidCatchRecordException.class);
        assertThatThrownBy(() -> validDetails(today, new BigDecimal("1000000.00"), null, today))
                .isInstanceOf(InvalidCatchRecordException.class);
        assertThatThrownBy(() -> validDetails(today, null, new BigDecimal("100000000.00"), today))
                .isInstanceOf(InvalidCatchRecordException.class);
    }

    @Test
    void rejectsMissingCaughtDateAndNonPositiveFishId() {
        // Bug caught: structurally incomplete details could be created without the validated factory.
        assertThatThrownBy(() -> new CatchRecordDetails(
                7L, null, "城郊水库", null, null, null, null))
                .isInstanceOf(InvalidCatchRecordException.class);
        assertThatThrownBy(() -> new CatchRecordDetails(
                0L, TODAY, "城郊水库", null, null, null, null))
                .isInstanceOf(InvalidCatchRecordException.class);
    }

    @Test
    void rejectsBlankAndOverlongLocation() {
        // Bug caught: empty or oversized required locations could violate input and column constraints.
        assertThatThrownBy(() -> validDetails(TODAY, "   ", null, null, null, null, TODAY))
                .isInstanceOf(InvalidCatchRecordException.class);
        assertThatThrownBy(() -> validDetails(TODAY, "x".repeat(201), null, null, null, null, TODAY))
                .isInstanceOf(InvalidCatchRecordException.class);
    }

    @Test
    void rejectsOverlongOptionalText() {
        // Bug caught: oversized optional fields could overflow their database columns.
        assertThatThrownBy(() -> validDetails(TODAY, "城郊水库", null, null, "x".repeat(101), null, TODAY))
                .isInstanceOf(InvalidCatchRecordException.class);
        assertThatThrownBy(() -> validDetails(TODAY, "城郊水库", null, null, null, "x".repeat(5_001), TODAY))
                .isInstanceOf(InvalidCatchRecordException.class);
    }

    @Test
    void rejectsMeasurementsWithMoreThanTwoSignificantDecimalPlaces() {
        // Bug caught: values incompatible with two-decimal storage precision could be accepted.
        assertThatThrownBy(() -> validDetails(TODAY, new BigDecimal("42.501"), null, TODAY))
                .isInstanceOf(InvalidCatchRecordException.class);
        assertThatThrownBy(() -> validDetails(TODAY, null, new BigDecimal("1350.001"), TODAY))
                .isInstanceOf(InvalidCatchRecordException.class);
    }

    @Test
    void acceptsZeroMeasurements() {
        // Bug caught: a non-negative constraint implemented as positive would reject a valid zero measurement.
        CatchRecordDetails details = validDetails(
                TODAY, new BigDecimal("0.00"), new BigDecimal("0.00"), TODAY);

        assertThat(details.lengthCm()).isEqualByComparingTo("0.00");
        assertThat(details.weightG()).isEqualByComparingTo("0.00");
    }

    @Test
    void createSetsBothTimestampsAndLeavesPersistenceFieldsUnset() {
        // Bug caught: a new record could accidentally look persisted or have divergent audit timestamps.
        CatchRecord record = CatchRecord.create(9L, validDetails(TODAY, null, null, TODAY), CREATED_AT);

        assertThat(record.id()).isNull();
        assertThat(record.photoObjectKey()).isNull();
        assertThat(record.createdAt()).isEqualTo(Instant.parse("2026-08-20T10:15:30Z"));
        assertThat(record.updatedAt()).isEqualTo(Instant.parse("2026-08-20T10:15:30Z"));
    }

    @Test
    void restoreAcceptsStructurallyValidPersistedDetailsWithoutRecheckingToday() {
        // Bug caught: hydration could reject a persisted catch merely because its date is later than a current clock.
        CatchRecordDetails persistedDetails = new CatchRecordDetails(
                7L, LocalDate.parse("2099-01-01"), "城郊水库", null, null, null, null);

        CatchRecord record = CatchRecord.restore(
                12L, 9L, persistedDetails, "catch/12/photo.jpg", CREATED_AT, UPDATED_AT);

        assertThat(record.details().caughtOn()).isEqualTo(LocalDate.parse("2099-01-01"));
        assertThat(record.photoObjectKey()).isEqualTo("catch/12/photo.jpg");
    }

    @Test
    void updatePreservesIdentityCreationTimestampAndPhotoObjectKey() {
        // Bug caught: editing details could discard immutable persistence and ownership data.
        CatchRecord record = CatchRecord.restore(
                12L, 9L, validDetails(TODAY, null, null, TODAY),
                "catch/12/photo.jpg", CREATED_AT, UPDATED_AT);
        CatchRecord updated = record.update(
                validDetails(TODAY, new BigDecimal("45.00"), null, TODAY),
                Instant.parse("2026-08-20T12:15:30Z"));

        assertThat(updated.id()).isEqualTo(12L);
        assertThat(updated.userId()).isEqualTo(9L);
        assertThat(updated.createdAt()).isEqualTo(Instant.parse("2026-08-20T10:15:30Z"));
        assertThat(updated.photoObjectKey()).isEqualTo("catch/12/photo.jpg");
        assertThat(updated.updatedAt()).isEqualTo(Instant.parse("2026-08-20T12:15:30Z"));
        assertThat(updated.details().lengthCm()).isEqualByComparingTo("45.00");
    }

    @Test
    void rejectsInvalidRecordIdsMissingDetailsAndMissingTimestamps() {
        // Bug caught: records with invalid persistence identity or audit data could enter the domain.
        CatchRecordDetails details = validDetails(TODAY, null, null, TODAY);

        assertThatThrownBy(() -> new CatchRecord(0L, 9L, details, null, CREATED_AT, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatchRecord(1L, 0L, details, null, CREATED_AT, UPDATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatchRecord(1L, 9L, null, null, CREATED_AT, UPDATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CatchRecord(1L, 9L, details, null, null, UPDATED_AT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposesStableDomainExceptionCodes() {
        // Bug caught: callers could lose their stable way to classify domain failures.
        assertThat(new InvalidCatchRecordException("invalid").code())
                .isEqualTo("INVALID_CATCH_RECORD");
        assertThat(new CatchRecordNotFoundException(12L).code())
                .isEqualTo("CATCH_RECORD_NOT_FOUND");
    }

    private static CatchRecordDetails validDetails(
            LocalDate caughtOn, BigDecimal lengthCm, BigDecimal weightG, LocalDate today) {
        return validDetails(caughtOn, "城郊水库", lengthCm, weightG, "岸钓", "晴天", today);
    }

    private static CatchRecordDetails validDetails(
            LocalDate caughtOn, String location, BigDecimal lengthCm, BigDecimal weightG,
            String method, String notes, LocalDate today) {
        return CatchRecordDetails.validated(
                7L, caughtOn, location, lengthCm, weightG, method, notes, today);
    }
}
