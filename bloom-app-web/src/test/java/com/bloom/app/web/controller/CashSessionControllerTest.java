package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.cashsession.CashReconciliationResponse;
import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.service.CashSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
