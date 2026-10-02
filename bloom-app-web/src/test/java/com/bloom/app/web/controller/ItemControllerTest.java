package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.service.ItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemControllerTest {
    private ItemService itemService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        itemService = mock(ItemService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ItemController(itemService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void listResponseContainsCompleteInventoryReadContract() throws Exception {
        ItemResponse item = completeItemResponse();
        when(itemService.filterItems(any(), any()))
            .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/items"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].active").value(false))
            .andExpect(jsonPath("$.data.content[0].hasStockMovements").value(true))
            .andExpect(jsonPath("$.data.content[0].baseUnitOfMeasureLocked").value(true))
            .andExpect(jsonPath("$.data.content[0].fractionalQuantityAllowedLocked").value(true))
            .andExpect(jsonPath("$.data.content[0].stockStore").value(1.2500))
            .andExpect(jsonPath("$.data.content[0].stockWarehouse").value(0.0001))
            .andExpect(jsonPath("$.data.content[0].baseUnitOfMeasure").value("METER"))
            .andExpect(jsonPath("$.data.content[0].fractionalQuantityAllowed").value(true));
    }

    @Test
    void detailResponseContainsCompleteInventoryReadContract() throws Exception {
        when(itemService.getItemDetails("ITEM-1")).thenReturn(completeItemResponse());

        mockMvc.perform(get("/api/items/ITEM-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active").value(false))
            .andExpect(jsonPath("$.data.hasStockMovements").value(true))
            .andExpect(jsonPath("$.data.baseUnitOfMeasureLocked").value(true))
            .andExpect(jsonPath("$.data.fractionalQuantityAllowedLocked").value(true))
            .andExpect(jsonPath("$.data.stockStore").value(1.2500))
            .andExpect(jsonPath("$.data.stockWarehouse").value(0.0001))
            .andExpect(jsonPath("$.data.baseUnitOfMeasure").value("METER"))
            .andExpect(jsonPath("$.data.fractionalQuantityAllowed").value(true));
    }

    @Test
    void categoryBarcodeReturnsPdfAttachmentAndPassesExactCategoryCode() throws Exception {
        byte[] pdfBytes = new byte[] {1, 2, 3};
        when(itemService.generateCategoryBarcodePdf("KAIN")).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/items/barcode/category/KAIN"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string(
                "Content-Disposition",
                "attachment; filename=\"barcodes-category-KAIN.pdf\""
            ))
            .andExpect(content().bytes(pdfBytes));

        verify(itemService).generateCategoryBarcodePdf("KAIN");
    }

    @Test
    void categoryBarcodePropagatesCategoryNotFoundAsJsonError() throws Exception {
        when(itemService.generateCategoryBarcodePdf("MISSING"))
            .thenThrow(new ResourceNotFoundException("Item category not found"));

        mockMvc.perform(get("/api/items/barcode/category/MISSING"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Item category not found"))
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.errorType").value("ResourceNotFoundException"));
    }

    @Test
    void categoryBarcodeLimitFailureReturnsJsonErrorInsteadOfPdf() throws Exception {
        when(itemService.generateCategoryBarcodePdf("KAIN"))
            .thenThrow(new IllegalArgumentException("Cannot request more than 100 barcodes at a time"));

        mockMvc.perform(get("/api/items/barcode/category/KAIN"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message")
                .value("Cannot request more than 100 barcodes at a time"))
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.errorType").value("IllegalArgumentException"));
    }

    private ItemResponse completeItemResponse() {
        return ItemResponse.builder()
            .sku("ITEM-1")
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .fractionalQuantityAllowed(true)
            .stockStore(new BigDecimal("1.2500"))
            .stockWarehouse(new BigDecimal("0.0001"))
            .active(false)
            .hasStockMovements(true)
            .baseUnitOfMeasureLocked(true)
            .fractionalQuantityAllowedLocked(true)
            .build();
    }
}
