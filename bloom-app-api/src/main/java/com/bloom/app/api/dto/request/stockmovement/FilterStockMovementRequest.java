package com.bloom.app.api.dto.request.stockmovement;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterStockMovementRequest {
    private Long itemId;
    private String itemSku;
    private MovementSourceType sourceType;
    private MovementType movementType;
    private StockLocation location;
    private Instant startDate;
    private Instant endDate;
    private String reference;
}
