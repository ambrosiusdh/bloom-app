package com.bloom.app.api.dto.request.stockadjustment;

import com.bloom.app.validation.UniqueBy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStockAdjustmentRequest {
    @NotBlank(message = "Reason is required")
    private String reason;

    @NotEmpty
    @Valid
    @UniqueBy(property = "itemSku")
    private List<StockAdjustmentItemRequest> items;
}
