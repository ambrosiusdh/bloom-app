package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.api.dto.response.sale.SaleCheckoutStatusResponse;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.api.dto.response.saleitem.SaleItemResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.domain.enums.PaymentType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.BusinessException;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.CheckoutIdempotencyConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.service.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SaleControllerTest {
    private static final String VALID_CHECKOUT_BODY = """
        {
          "discountAmount": 0.0000,
          "paidAmount": 10.0000,
          "paymentType": "CASH",
          "saleItemList": [
            {"itemSku": "ITEM-1", "quantity": 1.0000, "stockLocation": "STORE"}
          ]
        }
        """;

    private SaleService saleService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        saleService = mock(SaleService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SaleController(saleService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void postPassesIdempotencyKeyAndRequestToService() throws Exception {
        when(saleService.createSale(eq(" checkout-1 "), any())).thenReturn(
            SaleResponse.builder().code("SALE-1").build());

        mockMvc.perform(post("/api/sales")
                .header("Idempotency-Key", " checkout-1 ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.code").value("SALE-1"));

        verify(saleService).createSale(eq(" checkout-1 "),
            org.mockito.ArgumentMatchers.argThat(request ->
                request.getPaymentType() == PaymentType.CASH
                    && request.getPaidAmount().compareTo(new BigDecimal("10.0000")) == 0
                    && request.getSaleItemList().size() == 1
                    && request.getSaleItemList().getFirst().getStockLocation()
                        == StockLocation.STORE));
    }

    @Test
    void postRejectsMissingBlankAndOverlongIdempotencyKeys() throws Exception {
        String overlongKey = "x".repeat(101);
        when(saleService.createSale(eq("   "), any()))
            .thenThrow(new IllegalArgumentException("Idempotency-Key header is required"));
        when(saleService.createSale(eq(overlongKey), any()))
            .thenThrow(new IllegalArgumentException(
                "Idempotency-Key must not exceed 100 characters"));

        mockMvc.perform(post("/api/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sales")
                .header("Idempotency-Key", "   ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/sales")
                .header("Idempotency-Key", overlongKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY))
            .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutStatusRequiresTheSameValidIdempotencyKey() throws Exception {
        String overlongKey = "x".repeat(101);
        when(saleService.getCheckoutStatus("  "))
            .thenThrow(new IllegalArgumentException("Idempotency-Key header is required"));
        when(saleService.getCheckoutStatus(overlongKey))
            .thenThrow(new IllegalArgumentException(
                "Idempotency-Key must not exceed 100 characters"));

        mockMvc.perform(get("/api/sales/checkout-status"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/sales/checkout-status")
                .header("Idempotency-Key", "  "))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/sales/checkout-status")
                .header("Idempotency-Key", overlongKey))
            .andExpect(status().isBadRequest());
    }

    @Test
    void completedLookupReturnsFullBackendConfirmedSale() throws Exception {
        SaleResponse sale = fullSaleResponse();
        when(saleService.getCheckoutStatus("checkout-complete")).thenReturn(
            SaleCheckoutStatusResponse.builder()
                .status(SaleCheckoutStatusResponse.Status.COMPLETED)
                .sale(sale)
                .build());

        mockMvc.perform(get("/api/sales/checkout-status")
                .header("Idempotency-Key", "checkout-complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.sale.code").value("SALE-20"))
            .andExpect(jsonPath("$.data.sale.sessionId").value(7))
            .andExpect(jsonPath("$.data.sale.subtotalAmount").value(12.5))
            .andExpect(jsonPath("$.data.sale.discountAmount").value(2.5))
            .andExpect(jsonPath("$.data.sale.totalAmount").value(10.0))
            .andExpect(jsonPath("$.data.sale.paidAmount").value(20.0))
            .andExpect(jsonPath("$.data.sale.changeAmount").value(10.0))
            .andExpect(jsonPath("$.data.sale.paymentType").value("CASH"))
            .andExpect(jsonPath("$.data.sale.saleItems[0].item.sku").value("ITEM-1"))
            .andExpect(jsonPath("$.data.sale.saleItems[0].quantity").value(1.25))
            .andExpect(jsonPath("$.data.sale.saleItems[0].unitPrice").value(10.0))
            .andExpect(jsonPath("$.data.sale.saleItems[0].subtotal").value(12.5))
            .andExpect(jsonPath("$.data.sale.createdAt").exists())
            .andExpect(jsonPath("$.data.sale.updatedAt").exists())
            .andExpect(jsonPath("$.data.sale.createdBy").value("admin"))
            .andExpect(jsonPath("$.data.sale.updatedBy").value("admin"))
            .andExpect(jsonPath("$.data.sale.checkoutRequestHash").doesNotExist())
            .andExpect(jsonPath("$.data.sale.checkoutIdempotencyKey").doesNotExist());
    }

    @Test
    void unknownLookupReturnsSuccessfulOutcomeWithNullSale() throws Exception {
        when(saleService.getCheckoutStatus("checkout-unknown")).thenReturn(
            SaleCheckoutStatusResponse.builder()
                .status(SaleCheckoutStatusResponse.Status.UNKNOWN)
                .sale(null)
                .build());

        mockMvc.perform(get("/api/sales/checkout-status")
                .header("Idempotency-Key", "checkout-unknown"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("UNKNOWN"))
            .andExpect(jsonPath("$.data.sale").value(nullValue()));
    }

    @Test
    void mapsIdempotencySessionAndStockConflictsToHttp409() throws Exception {
        when(saleService.createSale(eq("checkout-idempotency"), any()))
            .thenThrow(new CheckoutIdempotencyConflictException());
        mockMvc.perform(post("/api/sales")
                .header("Idempotency-Key", "checkout-idempotency")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorType")
                .value("CheckoutIdempotencyConflictException"));

        when(saleService.createSale(eq("checkout-session"), any()))
            .thenThrow(new CashSessionConflictException("Checkout requires an open cash session"));
        mockMvc.perform(post("/api/sales")
                .header("Idempotency-Key", "checkout-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorType").value("CashSessionConflictException"));

        when(saleService.createSale(eq("checkout-stock"), any()))
            .thenThrow(new BusinessException(
                ErrorCode.SALE_INSUFFICIENT_STOCK_STORE, "ITEM-1", StockLocation.STORE));
        mockMvc.perform(post("/api/sales")
                .header("Idempotency-Key", "checkout-stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("sale_insufficient_stock"));
    }

    @Test
    void validationAndMissingItemsKeepTheirDocumentedStatuses() throws Exception {
        mockMvc.perform(post("/api/sales")
                .header("Idempotency-Key", "checkout-precision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY.replace("1.0000", "1.00000")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorType").value("ValidationFailed"));

        mockMvc.perform(post("/api/sales")
                .header("Idempotency-Key", "checkout-enum")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY.replace("CASH", "CARD")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorType").value("HttpMessageNotReadableException"));

        when(saleService.createSale(eq("checkout-missing-item"), any()))
            .thenThrow(new ResourceNotFoundException("Item not found: ITEM-1"));
        mockMvc.perform(post("/api/sales")
                .header("Idempotency-Key", "checkout-missing-item")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorType").value("ResourceNotFoundException"));
    }

    @Test
    void unexpectedPersistenceDetailsAreNotExposed() throws Exception {
        when(saleService.createSale(eq("checkout-failure"), any()))
            .thenThrow(new RuntimeException("raw SQL constraint detail"));

        mockMvc.perform(post("/api/sales")
                .header("Idempotency-Key", "checkout-failure")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CHECKOUT_BODY))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
            .andExpect(jsonPath("$.errorType").value("InternalServerError"));
    }

    private SaleResponse fullSaleResponse() {
        Instant createdAt = Instant.parse("2026-08-29T01:02:03Z");
        return SaleResponse.builder()
            .code("SALE-20")
            .sessionId(7L)
            .subtotalAmount(new BigDecimal("12.5000"))
            .discountAmount(new BigDecimal("2.5000"))
            .totalAmount(new BigDecimal("10.0000"))
            .paidAmount(new BigDecimal("20.0000"))
            .changeAmount(new BigDecimal("10.0000"))
            .paymentType(PaymentType.CASH)
            .description("Recovered checkout")
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .createdBy("admin")
            .updatedBy("admin")
            .saleItems(List.of(SaleItemResponse.builder()
                .item(ItemResponse.builder().sku("ITEM-1").build())
                .quantity(new BigDecimal("1.2500"))
                .unitPrice(new BigDecimal("10.0000"))
                .subtotal(new BigDecimal("12.5000"))
                .build()))
            .build();
    }
}
