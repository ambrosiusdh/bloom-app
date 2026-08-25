package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.cashsession.CashReconciliationResponse;
import com.bloom.app.api.dto.response.cashsession.CashMovementResponse;
import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.service.CashSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CashSessionControllerTest {
    private CashSessionService cashSessionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cashSessionService = mock(CashSessionService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new CashSessionController(cashSessionService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void opensSessionUsingStandardCreatedResponse() throws Exception {
        when(cashSessionService.openSession(any())).thenReturn(CashSessionResponse.builder()
            .id(7L)
            .openingCash(new BigDecimal("100.0000"))
            .expectedClosingCash(new BigDecimal("100.0000"))
            .status(CashSessionStatus.OPEN)
            .build());

        mockMvc.perform(post("/api/cash-sessions/open")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingCash\":100.0000}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value(201))
            .andExpect(jsonPath("$.data.id").value(7))
            .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void returnsCalculatedExpectedCashUsingStandardOkResponse() throws Exception {
        when(cashSessionService.calculateExpectedCash(7L)).thenReturn(
            CashReconciliationResponse.builder()
                .sessionId(7L)
                .openingCash(new BigDecimal("100.0000"))
                .totalCashIn(new BigDecimal("50.0000"))
                .totalCashOut(new BigDecimal("20.0000"))
                .expectedClosingCash(new BigDecimal("130.0000"))
                .build());

        mockMvc.perform(get("/api/cash-sessions/7/expected-cash"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.expectedClosingCash").value(130.0));
    }

    @Test
    void returnsExistingCurrentSessionUsingUnchangedSuccessfulResponse() throws Exception {
        when(cashSessionService.getCurrentSession()).thenReturn(Optional.of(
            CashSessionResponse.builder()
                .id(7L)
                .openingCash(new BigDecimal("100.0000"))
                .expectedClosingCash(new BigDecimal("130.0000"))
                .openedAt(Instant.parse("2026-08-18T01:00:00Z"))
                .openedBy("admin")
                .status(CashSessionStatus.OPEN)
                .version(3L)
                .build()));

        mockMvc.perform(get("/api/cash-sessions/current"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Success"))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.id").value(7))
            .andExpect(jsonPath("$.data.openingCash").value(100.0))
            .andExpect(jsonPath("$.data.expectedClosingCash").value(130.0))
            .andExpect(jsonPath("$.data.actualClosingCash").value(nullValue()))
            .andExpect(jsonPath("$.data.difference").value(nullValue()))
            .andExpect(jsonPath("$.data.openedAt").exists())
            .andExpect(jsonPath("$.data.openedBy").value("admin"))
            .andExpect(jsonPath("$.data.closedAt").value(nullValue()))
            .andExpect(jsonPath("$.data.closedBy").value(nullValue()))
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    void returnsSuccessfulNullResponseWhenNoCurrentSessionExists() throws Exception {
        when(cashSessionService.getCurrentSession()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/cash-sessions/current"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("No cash session is currently open"))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void unknownSpecificSessionIdStillReturnsNotFound() throws Exception {
        when(cashSessionService.getSessionDetails(999L))
            .thenThrow(new ResourceNotFoundException("Cash session not found: 999"));

        mockMvc.perform(get("/api/cash-sessions/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Cash session not found: 999"));
    }

    @Test
    void returnsMovementsUsingExistingOneBasedPaginationConvention() throws Exception {
        CashMovementResponse movement = CashMovementResponse.builder()
            .id(99L)
            .referenceNo("SALE-99")
            .recordedAt(Instant.parse("2026-08-18T01:00:00Z"))
            .build();
        when(cashSessionService.getSessionMovements(eq(7L), any())).thenReturn(
            new PageImpl<>(List.of(movement), PageRequest.of(1, 1), 2));

        mockMvc.perform(get("/api/cash-sessions/7/movements")
                .param("page", "2")
                .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(99))
            .andExpect(jsonPath("$.data.totalElements").value(2));

        org.mockito.Mockito.verify(cashSessionService).getSessionMovements(
            eq(7L), argThat(pageable -> pageable.getPageNumber() == 1
                && pageable.getPageSize() == 1));
    }

    @Test
    void mapsDoubleCloseConflictToHttp409() throws Exception {
        when(cashSessionService.closeSession(eq(7L), any()))
            .thenThrow(new CashSessionConflictException("Cash session 7 is already closed"));

        mockMvc.perform(post("/api/cash-sessions/7/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actualClosingCash\":100.0000}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("Cash session 7 is already closed"));
    }

    @Test
    void rejectsMoneyWithMoreThanFourDecimalPlaces() throws Exception {
        mockMvc.perform(post("/api/cash-sessions/open")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingCash\":1.00000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorType").value("ValidationFailed"));
    }
}
