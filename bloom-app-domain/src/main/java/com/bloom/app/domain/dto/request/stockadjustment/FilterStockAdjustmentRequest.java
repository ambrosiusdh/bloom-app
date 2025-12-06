package com.bloom.app.domain.dto.request.stockadjustment;

import com.bloom.app.domain.enums.StockAdjustmentSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterStockAdjustmentRequest {
    private String stockAdjustmentCode;
    private StockAdjustmentSource source;
    private Instant startDate;
    private Instant endDate;
}
