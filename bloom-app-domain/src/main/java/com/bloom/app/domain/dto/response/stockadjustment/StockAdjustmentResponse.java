package com.bloom.app.domain.dto.response.stockadjustment;

import com.bloom.app.domain.enums.StockAdjustmentSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentResponse {
    private Long id;
    private String stockAdjustmentCode;
    private String reason;
    private StockAdjustmentSource source;
    private String createdBy;
    private Instant createdAt;
    private List<StockAdjustmentItemResponse> items;
}
