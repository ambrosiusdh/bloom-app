package com.bloom.app.persistence.projection;

import java.math.BigDecimal;

public interface DashboardSupplierPayablesTotals {
    BigDecimal getOutstandingAmount();

    long getOpenReceiptCount();
}
