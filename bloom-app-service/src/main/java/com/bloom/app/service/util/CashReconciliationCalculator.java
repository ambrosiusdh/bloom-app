package com.bloom.app.service.util;

import com.bloom.app.domain.model.CashSession;
import com.bloom.app.persistence.projection.CashMovementTotals;
import com.bloom.app.persistence.repository.CashMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class CashReconciliationCalculator {
    private final CashMovementRepository cashMovementRepository;

    public Calculation calculate(CashSession session) {
        CashMovementTotals totals = cashMovementRepository.sumAmountsBySession(session.getId());
        BigDecimal totalIn = normalized(totals.totalCashIn());
        BigDecimal totalOut = normalized(totals.totalCashOut());
        BigDecimal expected = CashMoneyUtil.reconciliationBoundary(
            session.getOpeningCash().add(totalIn).subtract(totalOut));
        return new Calculation(totalIn, totalOut, expected);
    }

    private BigDecimal normalized(BigDecimal total) {
        return CashMoneyUtil.reconciliationBoundary(total == null ? BigDecimal.ZERO : total);
    }

    public record Calculation(
        BigDecimal totalCashIn,
        BigDecimal totalCashOut,
        BigDecimal expectedClosingCash
    ) {
    }
}
