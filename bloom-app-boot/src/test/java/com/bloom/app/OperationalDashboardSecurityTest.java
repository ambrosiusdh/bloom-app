package com.bloom.app;

import com.bloom.app.api.dto.response.dashboard.DashboardCashSessionState;
import com.bloom.app.api.dto.response.dashboard.DashboardCurrentCashSessionResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardDrillDownDestination;
import com.bloom.app.api.dto.response.dashboard.DashboardDrillDownResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardSalesTodayResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardSupplierPayablesResponse;
import com.bloom.app.api.dto.response.dashboard.OperationalDashboardResponse;
import com.bloom.app.config.SecurityConfig;
import com.bloom.app.domain.properties.CorsProperties;
import com.bloom.app.service.DashboardService;
import com.bloom.app.web.controller.DashboardController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(CorsProperties.class)
class OperationalDashboardSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void anonymousRequestReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/operational-overview"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "cashier", roles = "USER")
    void anyAuthenticatedUserReceivesSuccessfulOperationalDashboard() throws Exception {
        when(dashboardService.getOperationalOverview()).thenReturn(emptyResponse());

        mockMvc.perform(get("/api/dashboard/operational-overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.currentCashSession.state").value("NONE"));
    }

    private OperationalDashboardResponse emptyResponse() {
        LocalDate date = LocalDate.parse("2026-09-12");
        DashboardDrillDownResponse salesDrill = DashboardDrillDownResponse.builder()
            .destination(DashboardDrillDownDestination.SALES_HISTORY)
            .startDate(date)
            .endDate(date)
            .build();
        return OperationalDashboardResponse.builder()
            .asOf(Instant.parse("2026-09-11T18:30:00Z"))
            .freshUntil(Instant.parse("2026-09-11T18:35:00Z"))
            .businessDate(date)
            .storeZoneId("Asia/Jakarta")
            .salesToday(DashboardSalesTodayResponse.builder()
                .salesAmount(new BigDecimal("0.0000"))
                .transactionCount(0)
                .periodStart(Instant.parse("2026-09-11T17:00:00Z"))
                .periodEndExclusive(Instant.parse("2026-09-12T17:00:00Z"))
                .drillDown(salesDrill)
                .build())
            .currentCashSession(DashboardCurrentCashSessionResponse.builder()
                .state(DashboardCashSessionState.NONE)
                .drillDowns(List.of(DashboardDrillDownResponse.builder()
                    .destination(DashboardDrillDownDestination.CASH_SESSION_HISTORY)
                    .build()))
                .build())
            .supplierPayables(DashboardSupplierPayablesResponse.builder()
                .outstandingAmount(new BigDecimal("0.0000"))
                .openReceiptCount(0)
                .drillDown(DashboardDrillDownResponse.builder()
                    .destination(DashboardDrillDownDestination.PAYABLES)
                    .build())
                .build())
            .build();
    }
}
