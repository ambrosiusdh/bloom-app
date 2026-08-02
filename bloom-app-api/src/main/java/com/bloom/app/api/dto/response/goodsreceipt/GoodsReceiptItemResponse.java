package com.bloom.app.api.dto.response.goodsreceipt;

import com.bloom.app.api.dto.response.item.ItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import com.bloom.app.domain.enums.StockLocation;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptItemResponse {
    private ItemResponse item;
    private BigDecimal quantity;
    private BigDecimal purchasePrice;
    private StockLocation stockLocation;
}
