package com.bloom.app.api.dto.response.dashboard;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class DashboardCurrentCashSessionResponse {
    DashboardCashSessionState state;
    Long sessionId;
    Instant openedAt;
    String openedBy;
    BigDecimal openingCash;
    BigDecimal totalCashIn;
    BigDecimal totalCashOut;
    BigDecimal expectedClosingCash;
    BigDecimal activeExpenseAmount;
    Long activeExpenseCount;
    List<DashboardDrillDownResponse> drillDowns;
}
