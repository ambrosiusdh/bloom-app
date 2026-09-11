package com.bloom.app.persistence.projection;

import java.math.BigDecimal;

public interface DashboardExpenseTotals {
    BigDecimal getActiveExpenseAmount();

    long getActiveExpenseCount();
}
