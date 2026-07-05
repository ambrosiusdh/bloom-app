package com.bloom.app.api.dto.request.saleitem;

import com.bloom.app.domain.enums.StockLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateSaleItemRequest {
    private String itemSku;
    private Integer quantity;
    private StockLocation stockLocation;
}
