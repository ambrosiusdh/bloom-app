package com.bloom.app.api.dto.response.dashboard;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class DashboardSalesTodayResponse {
    BigDecimal salesAmount;
    long transactionCount;
    Instant periodStart;
    Instant periodEndExclusive;
    DashboardDrillDownResponse drillDown;
}
