package com.bloom.app.api.dto.request.saleitem;

import com.bloom.app.domain.enums.StockLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateSaleItemRequest {
    @NotNull
    private String itemSku;
    @NotNull
    @Positive
    private BigDecimal quantity;
    @NotNull
    private StockLocation stockLocation;
}
