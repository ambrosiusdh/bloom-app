package com.bloom.app.api.dto.response.stockmovement;

import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {
    private Long id;
    @JsonIgnore
    private Long legacyAuditLogId;
    private ItemResponse item;
    private MovementSourceType sourceType;
    private Long sourceId;
    private MovementType movementType;
    private StockAdjustmentActionType adjustmentActionType;
    private StockLocation location;
    private BigDecimal quantity;
    private BigDecimal qtyBefore;
    private BigDecimal qtyAfter;
    private String referenceNo;
    private String createdBy;
    private Instant createdAt;
}
