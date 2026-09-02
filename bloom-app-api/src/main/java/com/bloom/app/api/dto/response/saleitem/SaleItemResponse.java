package com.bloom.app.api.dto.response.saleitem;

import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.enums.StockLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SaleItemResponse {
    private ItemResponse item;
    private StockLocation stockLocation;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
