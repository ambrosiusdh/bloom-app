package com.bloom.app.api.dto.response.cashsession;

import com.bloom.app.domain.enums.CashSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashSessionResponse {
    private Long id;
    private BigDecimal openingCash;
    private BigDecimal expectedClosingCash;
    private BigDecimal actualClosingCash;
    private BigDecimal difference;
    private Instant openedAt;
    private String openedBy;
    private Instant closedAt;
    private String closedBy;
    private CashSessionStatus status;
    private Long version;
    private List<CashMovementResponse> movements;
}
