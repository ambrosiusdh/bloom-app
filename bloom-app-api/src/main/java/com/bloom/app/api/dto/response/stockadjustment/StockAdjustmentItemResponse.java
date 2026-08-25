package com.bloom.app.api.dto.response.stockadjustment;

import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentItemResponse {
    private Long id;
    private ItemResponse item;
    private StockAdjustmentActionType actionType;
    private StockLocation stockLocation;
    private BigDecimal changeQuantity;
    private BigDecimal previousStock;
    private BigDecimal newStock;
}
