package com.bloom.app.api.dto.response.dashboard;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class DashboardSalesLast7DaysResponse {
    LocalDate periodStartDate;
    LocalDate periodEndDate;
    BigDecimal totalSalesAmount;
    long totalTransactionCount;
    List<DashboardSalesDayResponse> days;
    DashboardDrillDownResponse drillDown;
}
