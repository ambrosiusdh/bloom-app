package com.bloom.app.api.dto.response.auditlog;

import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.enums.MovementSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Item-scoped read projection of the authoritative stock movement ledger. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemAuditLogResponse {
    private Long id;
    private ItemResponse item;
    private MovementSourceType source;
    private BigDecimal qty;
    private BigDecimal qtyBefore;
    private BigDecimal qtyAfter;
    private String referenceNo;
    private String createdBy;
    private Instant createdDate;
}
