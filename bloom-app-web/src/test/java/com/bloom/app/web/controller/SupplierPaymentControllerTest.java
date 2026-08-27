package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.supplierpayment.SupplierPaymentResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.domain.enums.SupplierPaymentMethod;
import com.bloom.app.service.SupplierPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupplierPaymentControllerTest {
    private SupplierPaymentService supplierPaymentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        supplierPaymentService = mock(SupplierPaymentService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SupplierPaymentController(supplierPaymentService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void recordsPaymentWithIdempotencyHeader() throws Exception {
        when(supplierPaymentService.createPayment(eq("GR-001"), eq("payment-001"), any()))
            .thenReturn(response(false));

        mockMvc.perform(post("/api/goods-receipts/GR-001/payments")
                .header("Idempotency-Key", "payment-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "amount": 25.0000,
                      "paymentMethod": "BANK_TRANSFER",
                      "paidAt": "2026-08-27T08:00:00Z",
                      "reference": "TRX-001",
                      "note": "First installment"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(41))
            .andExpect(jsonPath("$.data.paymentMethod").value("BANK_TRANSFER"))
            .andExpect(jsonPath("$.data.voided").value(false));
    }

    @Test
    void rejectsMissingHeaderAndUnsupportedPaymentMethod() throws Exception {
        String validBody = """
            {
              "amount": 25.0000,
              "paymentMethod": "CASH",
              "paidAt": "2026-08-27T08:00:00Z"
            }
            """;
        mockMvc.perform(post("/api/goods-receipts/GR-001/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/goods-receipts/GR-001/payments")
                .header("Idempotency-Key", "payment-unsupported")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "amount": 25.0000,
                      "paymentMethod": "CARD",
                      "paidAt": "2026-08-27T08:00:00Z"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returnsFullHistoryAndVoidsWithoutDeleteEndpoint() throws Exception {
        when(supplierPaymentService.getReceiptPaymentHistory(
            "GR-001", PageRequest.of(0, 10)))
            .thenReturn(new PageImpl<>(List.of(response(false)), PageRequest.of(0, 10), 1));
        when(supplierPaymentService.voidPayment(eq(41L), any()))
            .thenReturn(response(true));

        mockMvc.perform(get("/api/goods-receipts/GR-001/payments")
                .queryParam("page", "1")
                .queryParam("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(41));

        mockMvc.perform(post("/api/supplier-payments/41/void")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Duplicate transfer\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.voided").value(true))
            .andExpect(jsonPath("$.data.voidReason").value("Duplicate transfer"));
    }

    private SupplierPaymentResponse response(boolean voided) {
        return SupplierPaymentResponse.builder()
            .id(41L)
            .receiptId(21L)
            .receiptCode("GR-001")
            .supplierId(11L)
            .supplierCode("SUP-001")
            .amount(new BigDecimal("25.0000"))
            .paymentMethod(SupplierPaymentMethod.BANK_TRANSFER)
            .paidAt(Instant.parse("2026-08-27T08:00:00Z"))
            .actor("admin")
            .voided(voided)
            .voidReason(voided ? "Duplicate transfer" : null)
            .idempotencyKey("payment-001")
            .build();
    }
}
