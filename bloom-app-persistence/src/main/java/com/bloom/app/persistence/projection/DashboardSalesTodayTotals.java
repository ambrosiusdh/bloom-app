package com.bloom.app.persistence.projection;

import java.math.BigDecimal;

public interface DashboardSalesTodayTotals {
    BigDecimal getSalesAmount();

    long getTransactionCount();
}
