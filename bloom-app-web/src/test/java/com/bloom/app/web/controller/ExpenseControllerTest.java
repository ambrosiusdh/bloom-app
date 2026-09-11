package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.expense.ExpenseResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.domain.enums.ExpenseCategory;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.ExpenseIdempotencyConflictException;
import com.bloom.app.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpenseControllerTest {
    private ExpenseService expenseService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        expenseService = mock(ExpenseService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ExpenseController(expenseService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void createsExpenseUsingStandardCreatedResponse() throws Exception {
        when(expenseService.createExpense(any(), any())).thenReturn(ExpenseResponse.builder()
            .id(41L)
            .cashSessionId(7L)
            .amount(new BigDecimal("12.5000"))
            .category(ExpenseCategory.FOOD_AND_DRINK)
            .operationalExpense(true)
            .build());

        mockMvc.perform(post("/api/expenses")
                .header("Idempotency-Key", "expense-41")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"expectedCashSessionId":7,"amount":12.5000,"category":"FOOD_AND_DRINK","description":"Team meal"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(41))
            .andExpect(jsonPath("$.data.cashSessionId").value(7))
            .andExpect(jsonPath("$.data.category").value("FOOD_AND_DRINK"));
        verify(expenseService).createExpense(eq("expense-41"), argThat(request ->
            Long.valueOf(7L).equals(request.getExpectedCashSessionId())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"expectedCashSessionId\":null,",
        "\"expectedCashSessionId\":0,", "\"expectedCashSessionId\":-1,"})
    void rejectsMissingOrInvalidExpectedSession(String sessionField) throws Exception {
        mockMvc.perform(post("/api/expenses")
                .header("Idempotency-Key", "invalid-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + sessionField + "\"amount\":1,\"category\":\"CHARITY\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorType").value("ValidationFailed"))
            .andExpect(jsonPath("$.message[0].field").value("expectedCashSessionId"));
        verifyNoInteractions(expenseService);
    }

    @Test
    void preservesCashSessionAndIdempotencyConflictResponses() throws Exception {
        when(expenseService.createExpense(any(), any()))
            .thenThrow(new CashSessionConflictException("Expected cash session 7 is closed"))
            .thenThrow(new ExpenseIdempotencyConflictException());

        for (String errorType : List.of("CashSessionConflictException", "ExpenseIdempotencyConflictException")) {
            mockMvc.perform(post("/api/expenses")
                    .header("Idempotency-Key", "expense-41")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"expectedCashSessionId":7,"amount":1,"category":"CHARITY"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.errorType").value(errorType));
        }
    }

    @Test
    void rejectsInvalidMoneyOtherWithoutDescriptionAndBlankVoidReason() throws Exception {
        mockMvc.perform(post("/api/expenses")
                .header("Idempotency-Key", "expense-invalid-money")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedCashSessionId\":7,\"amount\":1.00000,\"category\":\"CHARITY\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorType").value("ValidationFailed"));

        mockMvc.perform(post("/api/expenses")
                .header("Idempotency-Key", "expense-invalid-category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedCashSessionId\":7,\"amount\":1.0000,\"category\":\"PERSONAL_MONEY\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorType").value("HttpMessageNotReadableException"));

        when(expenseService.createExpense(any(), any())).thenThrow(
            new IllegalArgumentException("Expense description is required for OTHER category"));
        mockMvc.perform(post("/api/expenses")
                .header("Idempotency-Key", "expense-other")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedCashSessionId\":7,\"amount\":1.0000,\"category\":\"OTHER\",\"description\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("Expense description is required for OTHER category"));

        mockMvc.perform(post("/api/expenses/41/void")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorType").value("ValidationFailed"));
    }

    @Test
    void requiresIdempotencyKeyForExpenseCreation() throws Exception {
        mockMvc.perform(post("/api/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedCashSessionId\":7,\"amount\":1.0000,\"category\":\"CHARITY\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void voidsAndListsWithExistingPaginationConvention() throws Exception {
        when(expenseService.voidExpense(eq(41L), any())).thenReturn(
            ExpenseResponse.builder().id(41L).voided(true).voidedReason("Duplicate").build());
        mockMvc.perform(post("/api/expenses/41/void")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Duplicate\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.voided").value(true));

        when(expenseService.getExpenses(any())).thenReturn(new PageImpl<>(
            List.of(ExpenseResponse.builder().id(41L).build()), PageRequest.of(1, 1), 2));
        mockMvc.perform(get("/api/expenses").param("page", "2").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(41));
        verify(expenseService).getExpenses(argThat(pageable ->
            pageable.getPageNumber() == 1 && pageable.getPageSize() == 1));
    }
}
