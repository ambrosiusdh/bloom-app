package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.service.StockMovementQueryService;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockMovementControllerTest {
    private StockMovementQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(StockMovementQueryService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new StockMovementController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void bindsEveryLedgerFilterAndUsesOneBasedPagination() throws Exception {
        StockMovementResponse response = StockMovementResponse.builder()
            .id(42L)
            .sourceType(MovementSourceType.SALE)
            .movementType(MovementType.OUT)
            .location(StockLocation.STORE)
            .referenceNo("SALE-42")
            .build();
        when(service.filterMovements(any(), any()))
            .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/stock-movements")
                .param("itemId", "9")
                .param("itemSku", "SKU-9")
                .param("sourceType", "SALE")
                .param("movementType", "OUT")
                .param("location", "STORE")
                .param("startDate", "2026-08-01T00:00:00Z")
                .param("endDate", "2026-08-31T23:59:59Z")
                .param("reference", "sale-42")
                .param("page", "2")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(42))
            .andExpect(jsonPath("$.data.content[0].referenceNo").value("SALE-42"))
            .andExpect(jsonPath("$.data.totalElements").value(6));

        verify(service).filterMovements(
            argThat(filter -> filter.getItemId().equals(9L)
                && filter.getItemSku().equals("SKU-9")
                && filter.getSourceType() == MovementSourceType.SALE
                && filter.getMovementType() == MovementType.OUT
                && filter.getLocation() == StockLocation.STORE
                && filter.getStartDate().equals(Instant.parse("2026-08-01T00:00:00Z"))
                && filter.getEndDate().equals(Instant.parse("2026-08-31T23:59:59Z"))
                && filter.getReference().equals("sale-42")),
            argThat(pageable -> pageable.getPageNumber() == 1 && pageable.getPageSize() == 5)
        );
    }

    @Test
    void returnsBadRequestForAnInvertedDateRange() throws Exception {
        doThrow(new IllegalArgumentException("startDate must not be after endDate"))
            .when(service).filterMovements(any(), any());

        mockMvc.perform(get("/api/stock-movements")
                .param("startDate", "2026-09-01T00:00:00Z")
                .param("endDate", "2026-08-01T00:00:00Z"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("startDate must not be after endDate"));
    }
}
