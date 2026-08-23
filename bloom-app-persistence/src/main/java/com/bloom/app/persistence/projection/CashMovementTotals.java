package com.bloom.app.persistence.projection;

import java.math.BigDecimal;

public record CashMovementTotals(BigDecimal totalCashIn, BigDecimal totalCashOut) {
}
