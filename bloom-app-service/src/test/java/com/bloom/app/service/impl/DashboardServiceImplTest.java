package com.bloom.app.service.impl;

import com.bloom.app.api.dto.response.dashboard.DashboardCashSessionState;
import com.bloom.app.api.dto.response.dashboard.DashboardDrillDownDestination;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.domain.model.User;
import com.bloom.app.domain.properties.BloomProperties;
import com.bloom.app.domain.properties.DashboardProperties;
import com.bloom.app.persistence.projection.DashboardExpenseTotals;
import com.bloom.app.persistence.projection.DashboardSalesTodayTotals;
import com.bloom.app.persistence.projection.DashboardSupplierPayablesTotals;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.persistence.repository.ExpenseRepository;
import com.bloom.app.persistence.repository.GoodsReceiptRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.SaleRepository;
import com.bloom.app.service.util.CashReconciliationCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {
    private SaleRepository saleRepository;
    private CashSessionRepository cashSessionRepository;
    private ExpenseRepository expenseRepository;
    private GoodsReceiptRepository goodsReceiptRepository;
    private CashReconciliationCalculator reconciliationCalculator;
    private DashboardProperties properties;
    private Instant asOf;
    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        saleRepository = mock(SaleRepository.class);
        cashSessionRepository = mock(CashSessionRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        goodsReceiptRepository = mock(GoodsReceiptRepository.class);
        reconciliationCalculator = mock(CashReconciliationCalculator.class);
        properties = new DashboardProperties();
        properties.setStoreZoneId(ZoneId.of("Asia/Jakarta"));
        properties.setFreshness(Duration.ofMinutes(5));
        asOf = Instant.parse("2026-09-11T18:30:00Z");
        service = new DashboardServiceImpl(
            saleRepository,
            mock(ItemRepository.class),
            new BloomProperties(),
            cashSessionRepository,
            expenseRepository,
            goodsReceiptRepository,
            reconciliationCalculator,
            properties,
            Clock.fixed(asOf, ZoneOffset.UTC));

        when(saleRepository.summarizeOperationalSales(any(), any()))
            .thenReturn(sales("0.0000", 0));
        when(goodsReceiptRepository.summarizeOperationalPayables())
            .thenReturn(payables("0.0000", 0));
        when(cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN))
            .thenReturn(Optional.empty());
    }

    @Test
    void fixedClockProducesJakartaDateAndHalfOpenUtcBoundaries() {
        var result = service.getOperationalOverview();

        assertThat(result.getAsOf()).isEqualTo(asOf);
        assertThat(result.getBusinessDate()).hasToString("2026-09-12");
        assertThat(result.getStoreZoneId()).isEqualTo("Asia/Jakarta");
        assertThat(result.getSalesToday().getPeriodStart())
            .isEqualTo(Instant.parse("2026-09-11T17:00:00Z"));
        assertThat(result.getSalesToday().getPeriodEndExclusive())
            .isEqualTo(Instant.parse("2026-09-12T17:00:00Z"));

        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(saleRepository).summarizeOperationalSales(start.capture(), end.capture());
        assertThat(start.getValue()).isEqualTo(Instant.parse("2026-09-11T17:00:00Z"));
        assertThat(end.getValue()).isEqualTo(Instant.parse("2026-09-12T17:00:00Z"));
    }

    @Test
    void zeroSalesAndPayablesAreNumericZerosWithRequiredDrillDowns() {
        var result = service.getOperationalOverview();

        assertThat(result.getSalesToday().getSalesAmount()).isEqualByComparingTo("0.0000");
        assertThat(result.getSalesToday().getTransactionCount()).isZero();
        assertThat(result.getSalesToday().getDrillDown().getDestination())
            .isEqualTo(DashboardDrillDownDestination.SALES_HISTORY);
        assertThat(result.getSalesToday().getDrillDown().getStartDate())
            .isEqualTo(result.getBusinessDate());
        assertThat(result.getSalesToday().getDrillDown().getEndDate())
            .isEqualTo(result.getBusinessDate());
        assertThat(result.getSupplierPayables().getOutstandingAmount())
            .isEqualByComparingTo("0.0000");
        assertThat(result.getSupplierPayables().getOpenReceiptCount()).isZero();
        assertThat(result.getSupplierPayables().getDrillDown().getDestination())
            .isEqualTo(DashboardDrillDownDestination.PAYABLES);
    }

    @Test
    void noOpenSessionUsesNoneAndNullSessionContext() {
        var current = service.getOperationalOverview().getCurrentCashSession();

        assertThat(current.getState()).isEqualTo(DashboardCashSessionState.NONE);
        assertThat(current.getSessionId()).isNull();
        assertThat(current.getOpenedAt()).isNull();
        assertThat(current.getOpenedBy()).isNull();
        assertThat(current.getOpeningCash()).isNull();
        assertThat(current.getTotalCashIn()).isNull();
        assertThat(current.getTotalCashOut()).isNull();
        assertThat(current.getExpectedClosingCash()).isNull();
        assertThat(current.getActiveExpenseAmount()).isNull();
        assertThat(current.getActiveExpenseCount()).isNull();
        assertThat(current.getDrillDowns()).singleElement().satisfies(drillDown -> {
            assertThat(drillDown.getDestination())
                .isEqualTo(DashboardDrillDownDestination.CASH_SESSION_HISTORY);
            assertThat(drillDown.getReference()).isNull();
            assertThat(drillDown.getStartDate()).isNull();
            assertThat(drillDown.getEndDate()).isNull();
        });
        verify(reconciliationCalculator, never()).calculate(any());
        verify(expenseRepository, never()).summarizeActiveDrawerExpenses(any());
    }

    @Test
    void openSessionUsesAuthoritativeReconciliationAndActiveDrawerExpenseAggregate() {
        CashSession session = CashSession.builder()
            .id(7L)
            .openedAt(Instant.parse("2026-09-11T17:05:00Z"))
            .openedBy(User.builder().username("cashier").build())
            .openingCash(new BigDecimal("100.0000"))
            .status(CashSessionStatus.OPEN)
            .build();
        when(cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN))
            .thenReturn(Optional.of(session));
        when(reconciliationCalculator.calculate(session)).thenReturn(
            new CashReconciliationCalculator.Calculation(
                new BigDecimal("80.0000"),
                new BigDecimal("12.5000"),
                new BigDecimal("167.5000")));
        when(expenseRepository.summarizeActiveDrawerExpenses(7L))
            .thenReturn(expenses("12.5000", 2));

        var current = service.getOperationalOverview().getCurrentCashSession();

        assertThat(current.getState()).isEqualTo(DashboardCashSessionState.OPEN);
        assertThat(current.getSessionId()).isEqualTo(7L);
        assertThat(current.getOpenedBy()).isEqualTo("cashier");
        assertThat(current.getOpeningCash()).isEqualByComparingTo("100.0000");
        assertThat(current.getTotalCashIn()).isEqualByComparingTo("80.0000");
        assertThat(current.getTotalCashOut()).isEqualByComparingTo("12.5000");
        assertThat(current.getExpectedClosingCash()).isEqualByComparingTo("167.5000");
        assertThat(current.getActiveExpenseAmount()).isEqualByComparingTo("12.5000");
        assertThat(current.getActiveExpenseCount()).isEqualTo(2L);
        assertThat(current.getDrillDowns()).extracting("destination")
            .containsExactly(
                DashboardDrillDownDestination.CASH_SESSION_DETAIL,
                DashboardDrillDownDestination.EXPENSE_HISTORY);
        assertThat(current.getDrillDowns().getFirst().getReference()).isEqualTo("7");
        assertThat(current.getDrillDowns().getLast().getReference()).isNull();
        verify(reconciliationCalculator).calculate(session);
    }

    @Test
    void openSessionWithoutExpensesReturnsZeroAmountAndCount() {
        CashSession session = CashSession.builder()
            .id(7L)
            .openedAt(asOf)
            .openedBy(User.builder().username("cashier").build())
            .openingCash(new BigDecimal("10.0000"))
            .status(CashSessionStatus.OPEN)
            .build();
        when(cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN))
            .thenReturn(Optional.of(session));
        when(reconciliationCalculator.calculate(session)).thenReturn(
            new CashReconciliationCalculator.Calculation(
                new BigDecimal("0.0000"), new BigDecimal("0.0000"),
                new BigDecimal("10.0000")));
        when(expenseRepository.summarizeActiveDrawerExpenses(7L))
            .thenReturn(expenses("0.0000", 0));

        var current = service.getOperationalOverview().getCurrentCashSession();

        assertThat(current.getActiveExpenseAmount()).isEqualByComparingTo("0.0000");
        assertThat(current.getActiveExpenseCount()).isZero();
    }

    @Test
    void freshnessComesFromConfigurationAndIsStrictlyAfterAsOf() {
        properties.setFreshness(Duration.ofMinutes(9));

        var result = service.getOperationalOverview();

        assertThat(result.getFreshUntil()).isEqualTo(asOf.plus(Duration.ofMinutes(9)));
        assertThat(result.getFreshUntil()).isAfter(result.getAsOf());
    }

    @Test
    void defensivePayablesFloorPreventsNegativeDashboardValue() {
        when(goodsReceiptRepository.summarizeOperationalPayables())
            .thenReturn(payables("-1.0000", 0));

        assertThat(service.getOperationalOverview().getSupplierPayables().getOutstandingAmount())
            .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void operationalReadNeverSavesDomainState() {
        service.getOperationalOverview();

        verify(saleRepository, never()).save(any());
        verify(cashSessionRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
        verify(goodsReceiptRepository, never()).save(any());
    }

    private DashboardSalesTodayTotals sales(String amount, long count) {
        return new DashboardSalesTodayTotals() {
            public BigDecimal getSalesAmount() {
                return new BigDecimal(amount);
            }

            public long getTransactionCount() {
                return count;
            }
        };
    }

    private DashboardExpenseTotals expenses(String amount, long count) {
        return new DashboardExpenseTotals() {
            public BigDecimal getActiveExpenseAmount() {
                return new BigDecimal(amount);
            }

            public long getActiveExpenseCount() {
                return count;
            }
        };
    }

    private DashboardSupplierPayablesTotals payables(String amount, long count) {
        return new DashboardSupplierPayablesTotals() {
            public BigDecimal getOutstandingAmount() {
                return new BigDecimal(amount);
            }

            public long getOpenReceiptCount() {
                return count;
            }
        };
    }
}
