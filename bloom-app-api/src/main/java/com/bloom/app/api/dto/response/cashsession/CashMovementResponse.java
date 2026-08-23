package com.bloom.app.api.dto.response.cashsession;

import com.bloom.app.domain.enums.CashMovementDirection;
import com.bloom.app.domain.enums.CashMovementSourceType;
import com.bloom.app.domain.enums.CashMovementType;
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
public class CashMovementResponse {
    private Long id;
    private CashMovementType movementType;
    private CashMovementSourceType sourceType;
    private Long sourceId;
    private String referenceNo;
    private BigDecimal amount;
    private CashMovementDirection direction;
    private Instant recordedAt;
    private String actor;
}
