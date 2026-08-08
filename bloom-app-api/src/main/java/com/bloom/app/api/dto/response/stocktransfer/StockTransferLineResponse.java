package com.bloom.app.api.dto.response.stocktransfer;

import com.bloom.app.api.dto.response.item.ItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferLineResponse {
    private Long id;
    private ItemResponse item;
    private BigDecimal quantity;
}
