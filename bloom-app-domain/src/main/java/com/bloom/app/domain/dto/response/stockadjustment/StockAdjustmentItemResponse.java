package com.bloom.app.domain.dto.response.stockadjustment;

import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentItemResponse {
    private Long id;
    private ItemResponse item;
    private StockAdjustmentActionType actionType;
    private Integer changeQuantity;
    private Integer previousStock;
    private Integer newStock;
}
