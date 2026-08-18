package com.bloom.app.service.support;

import com.bloom.app.domain.enums.CashMovementDirection;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.persistence.repository.CashMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class CashReconciliationCalculator {
    private final CashMovementRepository cashMovementRepository;

    public Calculation calculate(CashSession session) {
        BigDecimal totalIn = normalizedSum(session.getId(), CashMovementDirection.IN);
        BigDecimal totalOut = normalizedSum(session.getId(), CashMovementDirection.OUT);
        BigDecimal expected = CashMoney.reconciliationBoundary(
            session.getOpeningCash().add(totalIn).subtract(totalOut));
        return new Calculation(totalIn, totalOut, expected);
    }

    private BigDecimal normalizedSum(Long sessionId, CashMovementDirection direction) {
        BigDecimal total = cashMovementRepository.sumAmountBySessionAndDirection(sessionId, direction);
        return CashMoney.reconciliationBoundary(total == null ? BigDecimal.ZERO : total);
    }

    public record Calculation(
        BigDecimal totalCashIn,
        BigDecimal totalCashOut,
        BigDecimal expectedClosingCash
    ) {
    }
}
