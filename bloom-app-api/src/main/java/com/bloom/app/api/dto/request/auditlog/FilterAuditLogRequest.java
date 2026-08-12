package com.bloom.app.api.dto.request.auditlog;

import com.bloom.app.domain.enums.StockAdjustmentActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterAuditLogRequest {
    private String itemSku;
    private StockAdjustmentActionType actionType;
    private String referenceNo;
    private Instant startDate;
    /** Exact inclusive instant; calendar-day clients must supply their intended boundary. */
    private Instant endDate;
}
