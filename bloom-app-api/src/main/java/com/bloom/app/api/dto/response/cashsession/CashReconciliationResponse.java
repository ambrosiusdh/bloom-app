package com.bloom.app.api.dto.response.cashsession;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashReconciliationResponse {
    private Long sessionId;
    private BigDecimal openingCash;
    private BigDecimal totalCashIn;
    private BigDecimal totalCashOut;
    private BigDecimal expectedClosingCash;
}
