package com.bloom.app.domain.dto.request.auditlog;

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
public class FilterAuditLogRequest {
    private String itemSku;
    private StockAdjustmentActionType actionType;
    private StockAdjustmentSource source;
    private String referenceNo;
    private Instant startDate;
    private Instant endDate;
}
