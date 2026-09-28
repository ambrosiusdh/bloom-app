package com.bloom.app.service.impl;

import com.bloom.app.api.dto.response.dashboard.DashboardCashSessionState;
import com.bloom.app.api.dto.response.dashboard.DashboardDrillDownDestination;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.domain.model.User;
import com.bloom.app.domain.properties.BloomProperties;
import com.bloom.app.domain.properties.DashboardProperties;
import com.bloom.app.persistence.projection.DashboardExpenseTotals;
import com.bloom.app.persistence.projection.DashboardSalesDayTotals;
import com.bloom.app.persistence.projection.DashboardStockAttentionPreview;
import com.bloom.app.persistence.projection.DashboardStockAttentionTotals;
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
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {
    private SaleRepository saleRepository;
    private CashSessionRepository cashSessionRepository;
    private ExpenseRepository expenseRepository;
    private GoodsReceiptRepository goodsReceiptRepository;
    private ItemRepository itemRepository;
    private CashReconciliationCalculator reconciliationCalculator;
    private BloomProperties bloomProperties;
    private DashboardProperties properties;
    private Instant asOf;
    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        saleRepository = mock(SaleRepository.class);
        cashSessionRepository = mock(CashSessionRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        goodsReceiptRepository = mock(GoodsReceiptRepository.class);
        itemRepository = mock(ItemRepository.class);
        reconciliationCalculator = mock(CashReconciliationCalculator.class);
        bloomProperties = new BloomProperties();
        bloomProperties.setLowStockThreshold(new BigDecimal("10.0000"));
        bloomProperties.setStoreZoneId(ZoneId.of("Asia/Jakarta"));
        properties = new DashboardProperties();
        properties.setFreshness(Duration.ofMinutes(5));
        asOf = Instant.parse("2026-09-11T18:30:00Z");
        service = new DashboardServiceImpl(
            saleRepository,
            itemRepository,
            bloomProperties,
            cashSessionRepository,
            expenseRepository,
            goodsReceiptRepository,
            reconciliationCalculator,
            properties,
            Clock.fixed(asOf, ZoneOffset.UTC));

        when(saleRepository.summarizeOperationalSalesLast7Days(any(), any(), anyString()))
            .thenReturn(salesRows(
                LocalDate.parse("2026-09-06"), Map.of(), "0.0000", 0));
        when(itemRepository.summarizeDashboardStockAttention(any()))
            .thenReturn(stockTotals(0, 0));
        when(itemRepository.findDashboardStockAttentionPreview(any()))
            .thenReturn(List.of());
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

        ArgumentCaptor<LocalDate> start = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> end = ArgumentCaptor.forClass(LocalDate.class);
        verify(saleRepository).summarizeOperationalSalesLast7Days(
            start.capture(), end.capture(), org.mockito.ArgumentMatchers.eq("Asia/Jakarta"));
        assertThat(start.getValue()).isEqualTo(LocalDate.parse("2026-09-06"));
        assertThat(end.getValue()).isEqualTo(LocalDate.parse("2026-09-12"));
    }

    @Test
    void salesLast7DaysCrossesYearWithExplicitZerosAndRepositoryPeriodTotals() {
        asOf = Instant.parse("2027-01-01T18:30:00Z");
        service = serviceAt(asOf, ZoneId.of("Asia/Jakarta"));
        LocalDate start = LocalDate.parse("2026-12-27");
        when(saleRepository.summarizeOperationalSalesLast7Days(
            start, LocalDate.parse("2027-01-02"), "Asia/Jakarta"))
            .thenReturn(salesRows(start, Map.of(
                LocalDate.parse("2026-12-31"), new SalesValue("1.1250", 1),
                LocalDate.parse("2027-01-02"), new SalesValue("2.2500", 2)
            ), "3.3750", 3));

        var history = service.getOperationalOverview().getSalesLast7Days();

        assertThat(history.getDays()).hasSize(7);
        assertThat(history.getDays()).extracting(day -> day.getBusinessDate().toString())
            .containsExactly(
                "2026-12-27", "2026-12-28", "2026-12-29", "2026-12-30",
                "2026-12-31", "2027-01-01", "2027-01-02");
        assertThat(history.getDays().get(1).getSalesAmount()).isEqualByComparingTo("0.0000");
        assertThat(history.getDays().get(1).getTransactionCount()).isZero();
        assertThat(history.getTotalSalesAmount()).isEqualByComparingTo("3.3750");
        assertThat(history.getTotalTransactionCount()).isEqualTo(3);
        assertThat(history.getDrillDown().getStartDate()).isEqualTo(start);
        assertThat(history.getDrillDown().getEndDate())
            .isEqualTo(LocalDate.parse("2027-01-02"));
    }

    @Test
    void salesDaysUseConfiguredZoneAcrossDaylightOffsetBoundary() {
        ZoneId newYork = ZoneId.of("America/New_York");
        asOf = Instant.parse("2026-03-09T16:00:00Z");
        service = serviceAt(asOf, newYork);
        LocalDate start = LocalDate.parse("2026-03-03");
        when(saleRepository.summarizeOperationalSalesLast7Days(
            start, LocalDate.parse("2026-03-09"), "America/New_York"))
            .thenReturn(salesRows(start, Map.of(), "0.0000", 0));

        var daylightChangeDay = service.getOperationalOverview()
            .getSalesLast7Days().getDays().get(5);

        assertThat(daylightChangeDay.getBusinessDate())
            .isEqualTo(LocalDate.parse("2026-03-08"));
        assertThat(daylightChangeDay.getPeriodStart())
            .isEqualTo(Instant.parse("2026-03-08T05:00:00Z"));
        assertThat(daylightChangeDay.getPeriodEndExclusive())
            .isEqualTo(Instant.parse("2026-03-09T04:00:00Z"));
    }

    @Test
    void stockAttentionMapsStoreOnlyCountsThresholdAndBoundedPreview() {
        when(itemRepository.summarizeDashboardStockAttention(new BigDecimal("10.0000")))
            .thenReturn(stockTotals(2, 1));
        when(itemRepository.findDashboardStockAttentionPreview(new BigDecimal("10.0000")))
            .thenReturn(List.of(
                stockPreview(11L, "A", "Zero", "PIECE", "0.0000", "OUT_OF_STOCK"),
                stockPreview(12L, "B", "Fraction", "METER", "0.2500", "LOW_STOCK"),
                stockPreview(13L, "C", "Low", "PIECE", "9.9999", "LOW_STOCK")));

        var stock = service.getOperationalOverview().getStockAttention();

        assertThat(stock.getOutOfStockCount()).isEqualTo(2);
        assertThat(stock.getLowStockCount()).isEqualTo(1);
        assertThat(stock.getThreshold()).isEqualByComparingTo("10.0000");
        assertThat(stock.getLocation()).hasToString("STORE");
        assertThat(stock.getPreview()).extracting("itemId").containsExactly(11L, 12L, 13L);
        assertThat(stock.getPreview().get(1).getStockStore()).isEqualByComparingTo("0.2500");
        assertThat(stock.getDrillDown().getDestination())
            .isEqualTo(DashboardDrillDownDestination.ITEM_LIST);
    }

    @Test
    void operationalReadUsesFixedAggregateAndPreviewQueryShape() {
        service.getOperationalOverview();

        verify(saleRepository, times(1))
            .summarizeOperationalSalesLast7Days(any(), any(), anyString());
        verify(saleRepository, never()).findByCreatedAtBetween(any(), any());
        verify(itemRepository, times(1)).summarizeDashboardStockAttention(any());
        verify(itemRepository, times(1)).findDashboardStockAttentionPreview(any());
        verify(itemRepository, never()).findAll();
    }

    @Test
    void databaseFailureAbortsTheReadInsteadOfReturningPartialSections() {
        when(saleRepository.summarizeOperationalSalesLast7Days(any(), any(), anyString()))
            .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(service::getOperationalOverview)
            .isInstanceOf(DataAccessResourceFailureException.class);

        verify(itemRepository, never()).summarizeDashboardStockAttention(any());
        verify(itemRepository, never()).findDashboardStockAttentionPreview(any());
        verify(goodsReceiptRepository, never()).summarizeOperationalPayables();
        verify(cashSessionRepository, never()).findFirstByStatus(any());
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
        verify(itemRepository, never()).save(any());
    }

    private DashboardServiceImpl serviceAt(Instant instant, ZoneId zoneId) {
        bloomProperties.setStoreZoneId(zoneId);
        return new DashboardServiceImpl(
            saleRepository,
            itemRepository,
            bloomProperties,
            cashSessionRepository,
            expenseRepository,
            goodsReceiptRepository,
            reconciliationCalculator,
            properties,
            Clock.fixed(instant, ZoneOffset.UTC));
    }

    private List<DashboardSalesDayTotals> salesRows(
            LocalDate start,
            Map<LocalDate, SalesValue> values,
            String periodAmount,
            long periodCount) {
        return start.datesUntil(start.plusDays(7))
            .map(date -> {
                SalesValue value = values.getOrDefault(date, new SalesValue("0.0000", 0));
                return salesDay(date, value.amount(), value.count(), periodAmount, periodCount);
            })
            .toList();
    }

    private DashboardSalesDayTotals salesDay(
            LocalDate date,
            String amount,
            long count,
            String periodAmount,
            long periodCount) {
        return new DashboardSalesDayTotals() {
            public LocalDate getBusinessDate() { return date; }
            public BigDecimal getSalesAmount() { return new BigDecimal(amount); }
            public long getTransactionCount() { return count; }
            public BigDecimal getPeriodSalesAmount() { return new BigDecimal(periodAmount); }
            public long getPeriodTransactionCount() { return periodCount; }
        };
    }

    private DashboardStockAttentionTotals stockTotals(long out, long low) {
        return new DashboardStockAttentionTotals() {
            public long getOutOfStockCount() { return out; }
            public long getLowStockCount() { return low; }
        };
    }

    private DashboardStockAttentionPreview stockPreview(
            Long id,
            String sku,
            String name,
            String unit,
            String quantity,
            String state) {
        return new DashboardStockAttentionPreview() {
            public Long getItemId() { return id; }
            public String getSku() { return sku; }
            public String getName() { return name; }
            public String getBaseUnitOfMeasure() { return unit; }
            public BigDecimal getStockStore() { return new BigDecimal(quantity); }
            public String getState() { return state; }
        };
    }

    private record SalesValue(String amount, long count) {}

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
