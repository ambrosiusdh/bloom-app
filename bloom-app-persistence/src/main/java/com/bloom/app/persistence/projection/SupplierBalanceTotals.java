package com.bloom.app.persistence.projection;

import java.math.BigDecimal;

public interface SupplierBalanceTotals {
    BigDecimal getTotalPostedAmount();

    BigDecimal getPaidAmount();
}
