package com.bloom.app.api.dto.request.stockadjustment;

import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockLocation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentItemRequest {
    @NotNull
    private String itemSku;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most four decimal places")
    @Schema(description = "Positive delta for ADD/REMOVE; absolute target stock for CORRECTION. "
        + "At most four decimal places; direction must not be encoded with a negative value.",
        example = "0.2500")
    private BigDecimal changeQuantity;

    @NotNull
    @Schema(description = "ADD and REMOVE use changeQuantity as a positive delta; "
        + "CORRECTION uses it as the absolute target stock.")
    private StockAdjustmentActionType actionType;

    @NotNull
    @Schema(description = "Authoritative item balance location; only STORE or WAREHOUSE is supported.")
    private StockLocation stockLocation;
}
