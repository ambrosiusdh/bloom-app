package com.bloom.app.api.dto.response.dashboard;

import com.bloom.app.domain.model.UnitOfMeasure;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class DashboardStockAttentionItemResponse {
    Long itemId;
    String sku;
    String name;
    UnitOfMeasure baseUnitOfMeasure;
    BigDecimal stockStore;
    DashboardStockAttentionState state;
}
