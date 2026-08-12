package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.service.StockMovementQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemStockHistoryControllerTest {
    private StockMovementQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(StockMovementQueryService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ItemStockHistoryController(service))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void returnsItemAuditLogProjectionFromAuthoritativeLedger() throws Exception {
        ItemAuditLogResponse response = ItemAuditLogResponse.builder()
            .id(42L)
            .source(MovementSourceType.SALE)
            .referenceNo("SALE-42")
            .build();
        when(service.getItemAuditLogs(any(), any()))
            .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/items/SKU-9/audit-log")
                .param("page", "2")
                .param("size", "5")
                .param("sort", "createdDate,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(42))
            .andExpect(jsonPath("$.data.content[0].source").value("SALE"))
            .andExpect(jsonPath("$.data.content[0].referenceNo").value("SALE-42"))
            .andExpect(jsonPath("$.data.totalElements").value(6));

        verify(service).getItemAuditLogs(
            eq("SKU-9"),
            argThat(pageable -> pageable.getPageNumber() == 1
                && pageable.getPageSize() == 5
                && pageable.getSort().getOrderFor("createdDate").isDescending())
        );
    }
}
