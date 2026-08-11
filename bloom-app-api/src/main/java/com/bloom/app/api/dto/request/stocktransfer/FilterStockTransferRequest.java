package com.bloom.app.api.dto.request.stocktransfer;

import com.bloom.app.domain.enums.StockLocation;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterStockTransferRequest {
    private String code;

    @Positive(message = "Item ID must be positive")
    private Long itemId;

    private StockLocation sourceLocation;
    private StockLocation destinationLocation;
    private Instant createdFrom;
    private Instant createdTo;
}
