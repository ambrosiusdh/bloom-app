package com.bloom.app.service.specification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class GoodsReceiptSpecificationTest {
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Jakarta");

    @Test
    void createsStartInclusiveAndEndExclusiveStoreDayBoundaries() {
        assertThat(GoodsReceiptSpecification.startInclusive(
            LocalDate.parse("2026-09-12"), STORE_ZONE))
            .isEqualTo(Instant.parse("2026-09-11T17:00:00Z"));
        assertThat(GoodsReceiptSpecification.endExclusive(
            LocalDate.parse("2026-09-12"), STORE_ZONE))
            .isEqualTo(Instant.parse("2026-09-12T17:00:00Z"));
    }

    @Test
    void preservesMonthYearAndLeapDayBoundaries() {
        assertThat(GoodsReceiptSpecification.endExclusive(
            LocalDate.parse("2028-02-29"), STORE_ZONE))
            .isEqualTo(Instant.parse("2028-02-29T17:00:00Z"));
        assertThat(GoodsReceiptSpecification.endExclusive(
            LocalDate.parse("2026-12-31"), STORE_ZONE))
            .isEqualTo(Instant.parse("2026-12-31T17:00:00Z"));
    }
}
