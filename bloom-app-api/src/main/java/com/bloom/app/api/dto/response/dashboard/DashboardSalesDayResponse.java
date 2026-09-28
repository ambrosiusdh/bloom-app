package com.bloom.app.api.dto.response.dashboard;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Value
@Builder
public class DashboardSalesDayResponse {
    LocalDate businessDate;
    BigDecimal salesAmount;
    long transactionCount;
    Instant periodStart;
    Instant periodEndExclusive;
}
