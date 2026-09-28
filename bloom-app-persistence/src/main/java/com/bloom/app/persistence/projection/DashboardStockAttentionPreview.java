package com.bloom.app.persistence.projection;

import java.math.BigDecimal;

public interface DashboardStockAttentionPreview {
    Long getItemId();

    String getSku();

    String getName();

    String getBaseUnitOfMeasure();

    BigDecimal getStockStore();

    String getState();
}
