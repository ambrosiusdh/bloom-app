package com.bloom.app.api.dto.response.dashboard;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class DashboardSupplierPayablesResponse {
    BigDecimal outstandingAmount;
    long openReceiptCount;
    DashboardDrillDownResponse drillDown;
}
