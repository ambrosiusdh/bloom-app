package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.stocktransfer.StockTransferResponse;
import com.bloom.app.api.dto.response.stocktransfer.StockTransferSummaryResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.service.StockTransferService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockTransferControllerTest {
    private StockTransferService stockTransferService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stockTransferService = mock(StockTransferService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new StockTransferController(stockTransferService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void listsHistoryWithFiltersAndOneBasedPaginationConvention() throws Exception {
        StockTransferSummaryResponse summary = StockTransferSummaryResponse.builder()
            .id(42L)
            .code("ST/VIII-2026/0042")
            .sourceLocation(StockLocation.STORE)
            .destinationLocation(StockLocation.WAREHOUSE)
            .lineCount(2)
            .createdAt(Instant.parse("2026-08-11T12:00:00Z"))
            .build();
        when(stockTransferService.listStockTransfers(any(), any()))
            .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/stock-transfers")
                .param("code", "st / viii-2026 / 0042")
                .param("itemId", "9")
                .param("sourceLocation", "STORE")
                .param("destinationLocation", "WAREHOUSE")
                .param("createdFrom", "2026-08-01T00:00:00Z")
                .param("createdTo", "2026-08-31T23:59:59Z")
                .param("page", "2")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(42))
            .andExpect(jsonPath("$.data.content[0].lineCount").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(6));

        verify(stockTransferService).listStockTransfers(
            argThat(filter -> filter.getCode().equals("st / viii-2026 / 0042")
                && filter.getItemId().equals(9L)
                && filter.getSourceLocation() == StockLocation.STORE
                && filter.getDestinationLocation() == StockLocation.WAREHOUSE
                && filter.getCreatedFrom().equals(Instant.parse("2026-08-01T00:00:00Z"))
                && filter.getCreatedTo().equals(Instant.parse("2026-08-31T23:59:59Z"))),
            argThat(pageable -> pageable.getPageNumber() == 1 && pageable.getPageSize() == 5)
        );
    }

    @Test
    void retrievesDetailsByCanonicalCode() throws Exception {
        when(stockTransferService.getStockTransferDetails("TRF-00042"))
            .thenReturn(StockTransferResponse.builder()
                .id(42L).code("TRF-00042").build());

        mockMvc.perform(get("/api/stock-transfers/details").param("code", "TRF-00042"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(42))
            .andExpect(jsonPath("$.data.code").value("TRF-00042"));
    }

    @Test
    void returnsNotFoundForUnknownCanonicalCode() throws Exception {
        doThrow(new ResourceNotFoundException("Stock transfer not found: TRF-00404"))
            .when(stockTransferService).getStockTransferDetails("TRF-00404");

        mockMvc.perform(get("/api/stock-transfers/details").param("code", "TRF-00404"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("Stock transfer not found: TRF-00404"));
    }
}
