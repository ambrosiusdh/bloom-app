package com.bloom.app.domain.dto.response.auditlog;

import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockAdjustmentSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemAuditLogResponse {
    private Long id;
    private ItemResponse item;
    private StockAdjustmentActionType actionType;
    private Integer qty;
    private Integer qtyBefore;
    private Integer qtyAfter;
    private StockAdjustmentSource source;
    private String referenceNo;
    private String createdBy;
    private Instant createdDate;
}
