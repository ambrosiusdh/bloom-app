package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.stockadjustment.CreateStockAdjustmentResponse;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentItemResponse;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.service.StockAdjustmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockAdjustmentControllerTest {
    private StockAdjustmentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(StockAdjustmentService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new StockAdjustmentController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void rejectsMissingAndBlankReasonWithStandardValidationShape() throws Exception {
        String line = "\"items\":[{\"itemSku\":\"KAIN-001\",\"changeQuantity\":0.2500,"
            + "\"actionType\":\"REMOVE\",\"stockLocation\":\"STORE\"}]";

        mockMvc.perform(post("/api/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + line + "}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorType").value("ValidationFailed"))
            .andExpect(jsonPath("$.message[?(@.field == 'reason')]").isNotEmpty());

        String content = """
            {
              "reason": "  ",
              %s
            }
            """.formatted(line);
        mockMvc.perform(post("/api/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message[?(@.field == 'reason')]").isNotEmpty());
    }

    @Test
    void rejectsMissingLocationAndFifthDecimalPlace() throws Exception {
        mockMvc.perform(post("/api/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"Physical count","items":[{
                      "itemSku":"KAIN-001","changeQuantity":0.2500,"actionType":"REMOVE"
                    }]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message[?(@.field == 'items[0].stockLocation')]").isNotEmpty());

        mockMvc.perform(post("/api/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"Physical count","items":[{
                      "itemSku":"KAIN-001","changeQuantity":0.25000,
                      "actionType":"REMOVE","stockLocation":"STORE"
                    }]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message[?(@.field == 'items[0].changeQuantity')]").isNotEmpty());
    }

    @Test
    void rejectsUnsupportedEnumsAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"Physical count","items":[{
                      "itemSku":"KAIN-001","changeQuantity":0.2500,
                      "actionType":"DECREASE","stockLocation":"BACK_ROOM"
                    }]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.errorType").value("HttpMessageNotReadableException"));
    }

    @Test
    void returnsAdjustmentAndPersistedMovementsInPostingEnvelope() throws Exception {
        StockAdjustmentItemResponse line = StockAdjustmentItemResponse.builder()
            .id(81L)
            .actionType(StockAdjustmentActionType.REMOVE)
            .stockLocation(StockLocation.WAREHOUSE)
            .changeQuantity(new BigDecimal("0.2500"))
            .previousStock(new BigDecimal("5.0000"))
            .newStock(new BigDecimal("4.7500"))
            .build();
        StockAdjustmentResponse adjustment = StockAdjustmentResponse.builder()
            .id(42L)
            .stockAdjustmentCode("SA-000042")
            .reason("Physical count")
            .items(List.of(line))
            .build();
        StockMovementResponse movement = StockMovementResponse.builder()
            .id(151L)
            .sourceType(MovementSourceType.STOCK_ADJUSTMENT)
            .sourceId(42L)
            .movementType(MovementType.OUT)
            .adjustmentActionType(StockAdjustmentActionType.REMOVE)
            .location(StockLocation.WAREHOUSE)
            .quantity(new BigDecimal("0.2500"))
            .qtyBefore(new BigDecimal("5.0000"))
            .qtyAfter(new BigDecimal("4.7500"))
            .referenceNo("SA-000042")
            .build();
        when(service.createStockAdjustment(any())).thenReturn(CreateStockAdjustmentResponse.builder()
            .adjustment(adjustment)
            .movements(List.of(movement))
            .build());

        mockMvc.perform(post("/api/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"Physical count","items":[{
                      "itemSku":"KAIN-001","changeQuantity":0.2500,
                      "actionType":"REMOVE","stockLocation":"WAREHOUSE"
                    }]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.adjustment.id").value(42))
            .andExpect(jsonPath("$.data.adjustment.items[0].stockLocation").value("WAREHOUSE"))
            .andExpect(jsonPath("$.data.movements[0].id").value(151))
            .andExpect(jsonPath("$.data.movements[0].sourceType").value("STOCK_ADJUSTMENT"))
            .andExpect(jsonPath("$.data.movements[0].sourceId").value(42))
            .andExpect(jsonPath("$.data.movements[0].qtyBefore").value(5.0))
            .andExpect(jsonPath("$.data.movements[0].qtyAfter").value(4.75));
    }
}
