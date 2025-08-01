package com.bloom.app.domain.dto.request.saleitem;

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
}
