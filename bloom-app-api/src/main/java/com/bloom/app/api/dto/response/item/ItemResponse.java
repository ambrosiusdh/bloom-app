package com.bloom.app.api.dto.response.item;

import com.bloom.app.api.dto.response.itemcategory.ItemCategoryResponse;
import com.bloom.app.domain.model.UnitOfMeasure;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemResponse {
    private String name;
    private String sku;
    private String description;
    private BigDecimal price;
    private UnitOfMeasure baseUnitOfMeasure;
    private boolean fractionalQuantityAllowed;

    /**
     * Compatibility-only aggregate. New clients must use the location balances.
     */
    @Deprecated
    @Schema(
        description = "Deprecated aggregate of STORE and WAREHOUSE stock. Use stockStore and stockWarehouse instead.",
        deprecated = true
    )
    private BigDecimal stockQuantity;
    private BigDecimal stockStore;
    private BigDecimal stockWarehouse;
    private boolean active;
    private boolean hasStockMovements;
    private boolean baseUnitOfMeasureLocked;
    private boolean fractionalQuantityAllowedLocked;
    private ItemCategoryResponse category;

    private Instant createdAt;
    private Instant updatedAt;

    private String createdBy;
    private String updatedBy;
}
