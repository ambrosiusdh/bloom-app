package com.bloom.app.api.dto.request.stockadjustment;

import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockLocation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentItemRequest {
    @NotNull
    private String itemSku;

    @NotNull
    @PositiveOrZero
    private BigDecimal changeQuantity;

    @NotNull
    private StockAdjustmentActionType actionType;

    @NotNull
    private StockLocation stockLocation;
}
