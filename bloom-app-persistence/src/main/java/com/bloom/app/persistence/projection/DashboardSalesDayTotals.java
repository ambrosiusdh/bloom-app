package com.bloom.app.persistence.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DashboardSalesDayTotals {
    LocalDate getBusinessDate();

    BigDecimal getSalesAmount();

    long getTransactionCount();

    BigDecimal getPeriodSalesAmount();

    long getPeriodTransactionCount();
}
