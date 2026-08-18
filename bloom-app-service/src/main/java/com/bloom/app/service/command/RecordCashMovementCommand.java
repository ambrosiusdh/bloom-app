package com.bloom.app.service.command;

import com.bloom.app.domain.enums.CashMovementDirection;
import com.bloom.app.domain.enums.CashMovementSourceType;
import com.bloom.app.domain.enums.CashMovementType;

import java.math.BigDecimal;

public record RecordCashMovementCommand(
    Long sessionId,
    CashMovementType movementType,
    CashMovementSourceType sourceType,
    Long sourceId,
    String referenceNo,
    BigDecimal amount,
    CashMovementDirection direction,
    String idempotencyKey
) {
}
