package com.bloom.app.api.dto.response.stockadjustment;

import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStockAdjustmentResponse {
    private StockAdjustmentResponse adjustment;
    private List<StockMovementResponse> movements;
}
