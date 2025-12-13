package com.bloom.app.api.dto.request.stockadjustment;

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
    private Instant startDate;
    private Instant endDate;
}
