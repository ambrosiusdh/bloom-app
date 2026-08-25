package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.domain.enums.GoodsReceiptStatus;
import com.bloom.app.service.GoodsReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoodsReceiptControllerTest {
    private GoodsReceiptService goodsReceiptService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        goodsReceiptService = mock(GoodsReceiptService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new GoodsReceiptController(goodsReceiptService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void createsImmediatelyPostedReceiptUsingIdempotencyHeader() throws Exception {
        when(goodsReceiptService.createGoodsReceipt(eq("receipt-41"), any()))
            .thenReturn(GoodsReceiptResponse.builder()
                .id(41L)
                .code("GR/VIII-2026/0041")
                .supplierCode("SUP-1")
                .supplierName("Supplier One")
                .totalAmount(new BigDecimal("12.5000"))
                .status(GoodsReceiptStatus.POSTED)
                .build());

        mockMvc.perform(post("/api/goods-receipts")
                .header("Idempotency-Key", "receipt-41")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "receivedDate":"2026-08-25T10:00:00Z",
                      "supplierCode":"SUP-1",
                      "totalAmount":999999.0000,
                      "paidAmount":999999.0000,
                      "items":[{
                        "itemSku":"ITEM-1",
                        "quantity":1.2500,
                        "purchasePrice":10.0000,
                        "stockLocation":"STORE"
                      }]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(41))
            .andExpect(jsonPath("$.data.totalAmount").value(12.5))
            .andExpect(jsonPath("$.data.status").value("POSTED"));

        verify(goodsReceiptService).createGoodsReceipt(eq("receipt-41"), any());
    }

    @Test
    void validatesIdempotencyReceiptLinesAndCancellationReason() throws Exception {
        String validBody = """
            {
              "receivedDate":"2026-08-25T10:00:00Z",
              "supplierCode":"SUP-1",
              "items":[{
                "itemSku":"ITEM-1",
                "quantity":1.0000,
                "purchasePrice":1.0000,
                "stockLocation":"STORE"
              }]
            }
            """;
        mockMvc.perform(post("/api/goods-receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/goods-receipts")
                .header("Idempotency-Key", "invalid-price")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "receivedDate":"2026-08-25T10:00:00Z",
                      "supplierCode":"SUP-1",
                      "items":[{
                        "itemSku":"ITEM-1",
                        "quantity":1.0000,
                        "purchasePrice":0.0000,
                        "stockLocation":"STORE"
                      }]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorType").value("ValidationFailed"));

        mockMvc.perform(post("/api/goods-receipts/GR-41/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorType").value("ValidationFailed"));
    }

    @Test
    void cancelsThroughDedicatedCommandEndpoint() throws Exception {
        when(goodsReceiptService.cancelGoodsReceipt(eq("GR-41"), any()))
            .thenReturn(GoodsReceiptResponse.builder()
                .id(41L)
                .status(GoodsReceiptStatus.CANCELLED)
                .cancellationReason("Supplier return")
                .build());

        mockMvc.perform(post("/api/goods-receipts/GR-41/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Supplier return\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.cancellationReason").value("Supplier return"));
    }

    @Test
    void mapsDomainArgumentValidationToBadRequest() throws Exception {
        when(goodsReceiptService.createGoodsReceipt(eq("inactive-supplier"), any()))
            .thenThrow(new IllegalArgumentException("Supplier must be active: SUP-1"));

        mockMvc.perform(post("/api/goods-receipts")
                .header("Idempotency-Key", "inactive-supplier")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "receivedDate":"2026-08-25T10:00:00Z",
                      "supplierCode":"SUP-1",
                      "items":[{
                        "itemSku":"ITEM-1",
                        "quantity":1.0000,
                        "purchasePrice":1.0000,
                        "stockLocation":"STORE"
                      }]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.errorType").value("IllegalArgumentException"))
            .andExpect(jsonPath("$.message").value("Supplier must be active: SUP-1"));
    }
}
