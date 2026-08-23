package com.bloom.app.service;

import com.bloom.app.api.dto.response.cashsession.CashMovementResponse;
import com.bloom.app.service.command.RecordCashMovementCommand;

public interface CashMovementService {
    CashMovementResponse recordMovement(RecordCashMovementCommand command);
}
