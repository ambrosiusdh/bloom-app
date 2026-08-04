package com.bloom.app.api.dto.request.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import com.bloom.app.domain.model.UnitOfMeasure;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateItemRequest {
    private String name;
    private String sku;
    private String description;
    private BigDecimal price;
    private UnitOfMeasure baseUnitOfMeasure;
    private Boolean fractionalQuantityAllowed;
}
