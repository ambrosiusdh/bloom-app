package com.bloom.app.api.dto.response.saleitem;

import com.bloom.app.domain.model.UnitOfMeasure;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SaleItemProductResponse {
    private String sku;
    private String name;
    private UnitOfMeasure baseUnitOfMeasure;
}
