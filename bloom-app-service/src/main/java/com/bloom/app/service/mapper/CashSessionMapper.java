package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.domain.model.CashMovement;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.service.support.CashReconciliationCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CashSessionMapper {
    private final CashMovementMapper cashMovementMapper;

    public CashSessionResponse toResponse(
            CashSession session,
            CashReconciliationCalculator.Calculation reconciliation,
            List<CashMovement> movements) {
        return CashSessionResponse.builder()
            .id(session.getId())
            .openingCash(session.getOpeningCash())
            .expectedClosingCash(reconciliation.expectedClosingCash())
            .actualClosingCash(session.getActualClosingCash())
            .difference(session.getDifference())
            .openedAt(session.getOpenedAt())
            .openedBy(session.getOpenedBy().getUsername())
            .closedAt(session.getClosedAt())
            .closedBy(session.getClosedBy() == null ? null : session.getClosedBy().getUsername())
            .status(session.getStatus())
            .version(session.getVersion())
            .movements(movements.stream().map(cashMovementMapper::toResponse).toList())
            .build();
    }
}
