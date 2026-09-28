package com.bloom.app.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.bloom.app.api.dto.response.dashboard.DashboardCashSessionState;
import com.bloom.app.api.dto.response.dashboard.DashboardCurrentCashSessionResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardDrillDownDestination;
import com.bloom.app.api.dto.response.dashboard.DashboardDrillDownResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardSalesDayResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardSalesLast7DaysResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardSalesTodayResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardStockAttentionItemResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardStockAttentionResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardStockAttentionState;
import com.bloom.app.api.dto.response.dashboard.DashboardSupplierPayablesResponse;
import com.bloom.app.api.dto.response.dashboard.OperationalDashboardResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {
    private DashboardService dashboardService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void returnsExactOperationalDashboardShapeWithNumericDecimalsAndIsoTimeValues() throws Exception {
        when(dashboardService.getOperationalOverview()).thenReturn(openResponse());

        mockMvc.perform(get("/api/dashboard/operational-overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Success"))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.asOf").value("2026-09-11T18:30:00Z"))
            .andExpect(jsonPath("$.data.freshUntil").value("2026-09-11T18:35:00Z"))
            .andExpect(jsonPath("$.data.businessDate").value("2026-09-12"))
            .andExpect(jsonPath("$.data.storeZoneId").value("Asia/Jakarta"))
            .andExpect(jsonPath("$.data.salesToday.salesAmount").value(25.125))
            .andExpect(jsonPath("$.data.salesToday.transactionCount").value(2))
            .andExpect(jsonPath("$.data.salesToday.periodStart")
                .value("2026-09-11T17:00:00Z"))
            .andExpect(jsonPath("$.data.salesToday.periodEndExclusive")
                .value("2026-09-12T17:00:00Z"))
            .andExpect(jsonPath("$.data.salesToday.drillDown.destination")
                .value("SALES_HISTORY"))
            .andExpect(jsonPath("$.data.salesToday.drillDown.reference").value(nullValue()))
            .andExpect(jsonPath("$.data.salesLast7Days.periodStartDate").value("2026-09-06"))
            .andExpect(jsonPath("$.data.salesLast7Days.periodEndDate").value("2026-09-12"))
            .andExpect(jsonPath("$.data.salesLast7Days.totalSalesAmount").value(25.125))
            .andExpect(jsonPath("$.data.salesLast7Days.totalTransactionCount").value(2))
            .andExpect(jsonPath("$.data.salesLast7Days.days.length()").value(7))
            .andExpect(jsonPath("$.data.salesLast7Days.days[0].businessDate")
                .value("2026-09-06"))
            .andExpect(jsonPath("$.data.salesLast7Days.days[6].salesAmount").value(25.125))
            .andExpect(jsonPath("$.data.salesLast7Days.drillDown.destination")
                .value("SALES_HISTORY"))
            .andExpect(jsonPath("$.data.stockAttention.outOfStockCount").value(1))
            .andExpect(jsonPath("$.data.stockAttention.lowStockCount").value(1))
            .andExpect(jsonPath("$.data.stockAttention.threshold").value(10))
            .andExpect(jsonPath("$.data.stockAttention.location").value("STORE"))
            .andExpect(jsonPath("$.data.stockAttention.preview[0].itemId").value(11))
            .andExpect(jsonPath("$.data.stockAttention.preview[0].stockStore").value(0))
            .andExpect(jsonPath("$.data.stockAttention.preview[0].state")
                .value("OUT_OF_STOCK"))
            .andExpect(jsonPath("$.data.stockAttention.drillDown.destination")
                .value("ITEM_LIST"))
            .andExpect(jsonPath("$.data.currentCashSession.state").value("OPEN"))
            .andExpect(jsonPath("$.data.currentCashSession.sessionId").value(7))
            .andExpect(jsonPath("$.data.currentCashSession.totalCashIn").value(25.125))
            .andExpect(jsonPath("$.data.currentCashSession.totalCashOut").value(3.5))
            .andExpect(jsonPath("$.data.currentCashSession.expectedClosingCash").value(121.625))
            .andExpect(jsonPath("$.data.currentCashSession.activeExpenseAmount").value(3.5))
            .andExpect(jsonPath("$.data.currentCashSession.activeExpenseCount").value(1))
            .andExpect(jsonPath("$.data.currentCashSession.drillDowns[0].destination")
                .value("CASH_SESSION_DETAIL"))
            .andExpect(jsonPath("$.data.currentCashSession.drillDowns[0].reference").value("7"))
            .andExpect(jsonPath("$.data.currentCashSession.drillDowns[1].destination")
                .value("EXPENSE_HISTORY"))
            .andExpect(jsonPath("$.data.supplierPayables.outstandingAmount").value(19.875))
            .andExpect(jsonPath("$.data.supplierPayables.openReceiptCount").value(2))
            .andExpect(jsonPath("$.data.supplierPayables.drillDown.destination").value("PAYABLES"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "\"salesAmount\":25.1250")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("Rp"))));
    }

    @Test
    void noSessionAndAllZeroMetricsRemainSuccessful() throws Exception {
        when(dashboardService.getOperationalOverview()).thenReturn(emptyResponse());

        mockMvc.perform(get("/api/dashboard/operational-overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.salesToday.salesAmount").value(0))
            .andExpect(jsonPath("$.data.salesToday.transactionCount").value(0))
            .andExpect(jsonPath("$.data.salesLast7Days.days.length()").value(7))
            .andExpect(jsonPath("$.data.salesLast7Days.totalSalesAmount").value(0))
            .andExpect(jsonPath("$.data.salesLast7Days.totalTransactionCount").value(0))
            .andExpect(jsonPath("$.data.stockAttention.outOfStockCount").value(0))
            .andExpect(jsonPath("$.data.stockAttention.lowStockCount").value(0))
            .andExpect(jsonPath("$.data.stockAttention.preview").isEmpty())
            .andExpect(jsonPath("$.data.currentCashSession.state").value("NONE"))
            .andExpect(jsonPath("$.data.currentCashSession.sessionId").value(nullValue()))
            .andExpect(jsonPath("$.data.currentCashSession.openingCash").value(nullValue()))
            .andExpect(jsonPath("$.data.currentCashSession.activeExpenseAmount").value(nullValue()))
            .andExpect(jsonPath("$.data.currentCashSession.activeExpenseCount").value(nullValue()))
            .andExpect(jsonPath("$.data.currentCashSession.drillDowns[0].destination")
                .value("CASH_SESSION_HISTORY"))
            .andExpect(jsonPath("$.data.supplierPayables.outstandingAmount").value(0))
            .andExpect(jsonPath("$.data.supplierPayables.openReceiptCount").value(0));
    }

    @Test
    void legacyOverviewRemainsAvailable() throws Exception {
        DashboardResponse legacy = DashboardResponse.builder().summary(List.of()).build();
        when(dashboardService.getDashboardOverview()).thenReturn(legacy);

        mockMvc.perform(get("/api/dashboard/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.summary").isArray());
    }

    @Test
    void databaseFailureUsesNormalApiErrorWithoutPartialDashboardData() throws Exception {
        when(dashboardService.getOperationalOverview())
            .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        mockMvc.perform(get("/api/dashboard/operational-overview"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.errorType").value("InternalServerError"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    private OperationalDashboardResponse openResponse() {
        LocalDate date = LocalDate.parse("2026-09-12");
        return baseResponse()
            .salesToday(sales(date, "25.1250", 2))
            .salesLast7Days(salesLast7Days(date, "25.1250", 2))
            .stockAttention(DashboardStockAttentionResponse.builder()
                .outOfStockCount(1)
                .lowStockCount(1)
                .threshold(new BigDecimal("10.0000"))
                .location(StockLocation.STORE)
                .preview(List.of(DashboardStockAttentionItemResponse.builder()
                    .itemId(11L)
                    .sku("ITEM-11")
                    .name("Example")
                    .baseUnitOfMeasure(UnitOfMeasure.PIECE)
                    .stockStore(new BigDecimal("0.0000"))
                    .state(DashboardStockAttentionState.OUT_OF_STOCK)
                    .build()))
                .drillDown(drill(DashboardDrillDownDestination.ITEM_LIST))
                .build())
            .currentCashSession(DashboardCurrentCashSessionResponse.builder()
                .state(DashboardCashSessionState.OPEN)
                .sessionId(7L)
                .openedAt(Instant.parse("2026-09-11T17:05:00Z"))
                .openedBy("cashier")
                .openingCash(new BigDecimal("100.0000"))
                .totalCashIn(new BigDecimal("25.1250"))
                .totalCashOut(new BigDecimal("3.5000"))
                .expectedClosingCash(new BigDecimal("121.6250"))
                .activeExpenseAmount(new BigDecimal("3.5000"))
                .activeExpenseCount(1L)
                .drillDowns(List.of(
                    DashboardDrillDownResponse.builder()
                        .destination(DashboardDrillDownDestination.CASH_SESSION_DETAIL)
                        .reference("7")
                        .build(),
                    drill(DashboardDrillDownDestination.EXPENSE_HISTORY)))
                .build())
            .supplierPayables(payables("19.8750", 2))
            .build();
    }

    private OperationalDashboardResponse emptyResponse() {
        LocalDate date = LocalDate.parse("2026-09-12");
        return baseResponse()
            .salesToday(sales(date, "0.0000", 0))
            .salesLast7Days(salesLast7Days(date, "0.0000", 0))
            .stockAttention(DashboardStockAttentionResponse.builder()
                .outOfStockCount(0)
                .lowStockCount(0)
                .threshold(new BigDecimal("10.0000"))
                .location(StockLocation.STORE)
                .preview(List.of())
                .drillDown(drill(DashboardDrillDownDestination.ITEM_LIST))
                .build())
            .currentCashSession(DashboardCurrentCashSessionResponse.builder()
                .state(DashboardCashSessionState.NONE)
                .drillDowns(List.of(drill(
                    DashboardDrillDownDestination.CASH_SESSION_HISTORY)))
                .build())
            .supplierPayables(payables("0.0000", 0))
            .build();
    }

    private OperationalDashboardResponse.OperationalDashboardResponseBuilder baseResponse() {
        return OperationalDashboardResponse.builder()
            .asOf(Instant.parse("2026-09-11T18:30:00Z"))
            .freshUntil(Instant.parse("2026-09-11T18:35:00Z"))
            .businessDate(LocalDate.parse("2026-09-12"))
            .storeZoneId("Asia/Jakarta");
    }

    private DashboardSalesTodayResponse sales(LocalDate date, String amount, long count) {
        return DashboardSalesTodayResponse.builder()
            .salesAmount(new BigDecimal(amount))
            .transactionCount(count)
            .periodStart(Instant.parse("2026-09-11T17:00:00Z"))
            .periodEndExclusive(Instant.parse("2026-09-12T17:00:00Z"))
            .drillDown(DashboardDrillDownResponse.builder()
                .destination(DashboardDrillDownDestination.SALES_HISTORY)
                .startDate(date)
                .endDate(date)
                .build())
            .build();
    }

    private DashboardSupplierPayablesResponse payables(String amount, long count) {
        return DashboardSupplierPayablesResponse.builder()
            .outstandingAmount(new BigDecimal(amount))
            .openReceiptCount(count)
            .drillDown(drill(DashboardDrillDownDestination.PAYABLES))
            .build();
    }

    private DashboardSalesLast7DaysResponse salesLast7Days(
            LocalDate endDate,
            String amount,
            long count) {
        LocalDate startDate = endDate.minusDays(6);
        List<DashboardSalesDayResponse> days = startDate.datesUntil(endDate.plusDays(1))
            .map(date -> DashboardSalesDayResponse.builder()
                .businessDate(date)
                .salesAmount(date.equals(endDate) ? new BigDecimal(amount) : BigDecimal.ZERO)
                .transactionCount(date.equals(endDate) ? count : 0)
                .periodStart(date.atStartOfDay(java.time.ZoneId.of("Asia/Jakarta")).toInstant())
                .periodEndExclusive(date.plusDays(1)
                    .atStartOfDay(java.time.ZoneId.of("Asia/Jakarta")).toInstant())
                .build())
            .toList();
        return DashboardSalesLast7DaysResponse.builder()
            .periodStartDate(startDate)
            .periodEndDate(endDate)
            .totalSalesAmount(new BigDecimal(amount))
            .totalTransactionCount(count)
            .days(days)
            .drillDown(DashboardDrillDownResponse.builder()
                .destination(DashboardDrillDownDestination.SALES_HISTORY)
                .startDate(startDate)
                .endDate(endDate)
                .build())
            .build();
    }

    private DashboardDrillDownResponse drill(DashboardDrillDownDestination destination) {
        return DashboardDrillDownResponse.builder().destination(destination).build();
    }
}
