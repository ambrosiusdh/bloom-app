package com.bloom.app.domain.dto.response.saleitem;

import com.bloom.app.domain.dto.response.item.ItemResponse;
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
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
