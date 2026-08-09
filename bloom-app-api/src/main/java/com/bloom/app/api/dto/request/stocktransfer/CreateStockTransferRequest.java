package com.bloom.app.api.dto.request.stocktransfer;

import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.validation.UniqueBy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStockTransferRequest {
    @NotNull(message = "Source location is required")
    private StockLocation sourceLocation;

    @NotNull(message = "Destination location is required")
    private StockLocation destinationLocation;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    @NotEmpty(message = "Transfer lines are required")
    @UniqueBy(property = "itemSku", message = "Duplicate item lines are not allowed")
    private List<
        @NotNull(message = "Transfer line is required")
        @Valid StockTransferLineRequest
    > lines;
}
