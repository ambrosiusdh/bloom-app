package com.bloom.app.service.command;

import com.bloom.app.domain.enums.CashMovementType;

import java.math.BigDecimal;

public record RecordCashMovementCommand(
    Long sessionId,
    CashMovementType movementType,
    Long sourceId,
    String referenceNo,
    BigDecimal amount
) {
}
