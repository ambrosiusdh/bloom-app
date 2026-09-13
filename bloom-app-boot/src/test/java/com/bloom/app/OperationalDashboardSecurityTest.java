package com.bloom.app;

import com.bloom.app.api.dto.UserSessionData;
import com.bloom.app.api.dto.response.dashboard.DashboardCashSessionState;
import com.bloom.app.api.dto.response.dashboard.DashboardCurrentCashSessionResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardDrillDownDestination;
import com.bloom.app.api.dto.response.dashboard.DashboardDrillDownResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardSalesTodayResponse;
import com.bloom.app.api.dto.response.dashboard.DashboardSupplierPayablesResponse;
import com.bloom.app.api.dto.response.dashboard.OperationalDashboardResponse;
import com.bloom.app.config.SecurityConfig;
import com.bloom.app.domain.properties.CorsProperties;
import com.bloom.app.domain.exception.UserNotFoundException;
import com.bloom.app.domain.model.User;
import com.bloom.app.service.DashboardService;
import com.bloom.app.service.UserService;
import com.bloom.app.web.controller.DashboardController;
import com.bloom.app.web.security.AuthenticatedSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, AuthenticatedSessionService.class})
@EnableConfigurationProperties(CorsProperties.class)
class OperationalDashboardSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private UserService userService;

    @Test
    void anonymousRequestReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/operational-overview"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @WithMockUser(username = "cashier", roles = "USER")
    void anyAuthenticatedUserReceivesSuccessfulOperationalDashboard() throws Exception {
        when(dashboardService.getOperationalOverview()).thenReturn(emptyResponse());
        when(userService.findUserById(41L)).thenReturn(user(41L, "cashier"));
        MockHttpSession session = authenticatedSession("41", "cashier");

        mockMvc.perform(get("/api/dashboard/operational-overview").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.currentCashSession.state").value("NONE"));

        UserSessionData refreshed = (UserSessionData) session.getAttribute("currentUser");
        org.assertj.core.api.Assertions.assertThat(refreshed.getAccountId()).isEqualTo("41");
        org.assertj.core.api.Assertions.assertThat(refreshed.getName()).isEqualTo("Current cashier");
        org.assertj.core.api.Assertions.assertThat(refreshed.getRole()).isEqualTo("CASHIER");
    }

    @Test
    @WithMockUser(username = "cashier", roles = "USER")
    void deletedAccountCannotUseAnotherProtectedEndpointWithoutCallingCurrent() throws Exception {
        when(userService.findUserById(41L)).thenThrow(new UserNotFoundException("41"));
        MockHttpSession session = authenticatedSession("41", "cashier");

        mockMvc.perform(get("/api/dashboard/operational-overview").session(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.message")
                .value("Session identity expired; sign in again"));

        verify(dashboardService, never()).getOperationalOverview();
    }

    private MockHttpSession authenticatedSession(String accountId, String username) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", UserSessionData.builder()
            .accountId(accountId)
            .username(username)
            .name("Stale cashier")
            .role("ADMIN")
            .build());
        return session;
    }

    private User user(Long id, String username) {
        return User.builder()
            .id(id)
            .username(username)
            .name("Current cashier")
            .role("CASHIER")
            .build();
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
