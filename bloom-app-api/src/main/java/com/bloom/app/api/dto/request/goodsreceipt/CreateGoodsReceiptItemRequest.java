package com.bloom.app.api.dto.request.goodsreceipt;

import com.bloom.app.domain.enums.StockLocation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGoodsReceiptItemRequest {

    @NotBlank(message = "Item SKU is required")
    private String itemSku;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotNull(message = "Purchase price is required")
    @Positive(message = "Purchase price must be positive")
    @Digits(integer = 15, fraction = 4, message = "Purchase price must fit NUMERIC(19,4)")
    private BigDecimal purchasePrice;

    @NotNull(message = "Stock location is required")
    private StockLocation stockLocation;
}
