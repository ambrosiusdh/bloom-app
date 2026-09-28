package com.bloom.app.api.dto.response.dashboard;

import com.bloom.app.domain.enums.StockLocation;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class DashboardStockAttentionResponse {
    long outOfStockCount;
    long lowStockCount;
    BigDecimal threshold;
    StockLocation location;
    List<DashboardStockAttentionItemResponse> preview;
    DashboardDrillDownResponse drillDown;
}
