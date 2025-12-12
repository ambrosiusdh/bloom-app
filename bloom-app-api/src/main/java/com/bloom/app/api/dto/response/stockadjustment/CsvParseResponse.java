package com.bloom.app.api.dto.response.stockadjustment;

import com.bloom.app.domain.enums.StockAdjustmentActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvParseResponse {
    private String itemSku;
    private Integer changeQuantity;
    private StockAdjustmentActionType actionType;
    private String reason;
}
