package com.bloom.app.api.dto.request.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateItemRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String categoryCode;

    private String sku;
    private String description;

    @NotNull
    private BigDecimal price;

    @NotNull
    private UnitOfMeasure baseUnitOfMeasure;

    @NotNull
    @Builder.Default
    private Boolean fractionalQuantityAllowed = false;

    @NotNull
    @Builder.Default
    private Integer stockStore = 0;

    @NotNull
    @Builder.Default
    private Integer stockWarehouse = 0;
}
