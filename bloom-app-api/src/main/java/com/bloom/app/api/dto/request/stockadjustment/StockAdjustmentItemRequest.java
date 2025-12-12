package com.bloom.app.api.dto.request.stockadjustment;

import com.bloom.app.domain.enums.StockAdjustmentActionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentItemRequest {
    @NotNull
    private String itemSku;

    @NotNull
    private Integer changeQuantity;

    @NotNull
    private StockAdjustmentActionType actionType;
}
