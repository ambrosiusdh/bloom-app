package com.bloom.app.api.dto.response.goodsreceipt;

import com.bloom.app.api.dto.response.item.ItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.UnitOfMeasure;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptItemResponse {
    private Long id;
    private ItemResponse item;
    private BigDecimal quantity;
    private UnitOfMeasure baseUnitOfMeasure;
    private BigDecimal purchasePrice;
    private BigDecimal lineTotal;
    private StockLocation stockLocation;
}
