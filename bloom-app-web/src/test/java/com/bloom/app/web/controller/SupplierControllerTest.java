package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.supplier.SupplierResponse;
import com.bloom.app.api.dto.response.supplier.SupplierOutstandingBalanceResponse;
import com.bloom.app.api.exception.GlobalExceptionHandler;
import com.bloom.app.service.SupplierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupplierControllerTest {
    private SupplierService supplierService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        supplierService = mock(SupplierService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SupplierController(supplierService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void createReturnsSupplierResponseWithoutJpaIdentity() throws Exception {
        when(supplierService.createSupplier(any())).thenReturn(response(true));

        mockMvc.perform(post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "SUP-001",
                      "name": "Bloom Textile",
                      "contactNumber": "021-555",
                      "address": "Jakarta"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("SUP-001"))
            .andExpect(jsonPath("$.data.active").value(true))
            .andExpect(jsonPath("$.data.id").doesNotExist());
    }

    @Test
    void createRejectsBlankNameAndOverlongContact() throws Exception {
        String longContact = "1".repeat(256);

        mockMvc.perform(post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"SUP-001","name":" ","contactNumber":"%s"}
                    """.formatted(longContact)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorType").value("ValidationFailed"));
    }

    @Test
    void listPassesFiltersAndUsesRepositoryPagingConvention() throws Exception {
        when(supplierService.filterSuppliers(any(), eq(PageRequest.of(0, 10))))
            .thenReturn(new PageImpl<>(List.of(response(false)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/suppliers")
                .queryParam("query", "bloom")
                .queryParam("active", "false")
                .queryParam("page", "1")
                .queryParam("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].code").value("SUP-001"))
            .andExpect(jsonPath("$.data.content[0].active").value(false));
    }

    @Test
    void detailReturnsCompleteMasterData() throws Exception {
        when(supplierService.getSupplierDetails("SUP-001")).thenReturn(response(true));

        mockMvc.perform(get("/api/suppliers/SUP-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Bloom Textile"))
            .andExpect(jsonPath("$.data.contactNumber").value("021-555"))
            .andExpect(jsonPath("$.data.address").value("Jakarta"));
    }

    @Test
    void returnsDerivedOutstandingBalance() throws Exception {
        when(supplierService.getOutstandingBalance("SUP-001")).thenReturn(
            SupplierOutstandingBalanceResponse.builder()
                .supplierId(10L)
                .supplierCode("SUP-001")
                .supplierName("Bloom Textile")
                .totalPostedAmount(new BigDecimal("100.0000"))
                .paidAmount(new BigDecimal("40.0000"))
                .outstandingAmount(new BigDecimal("60.0000"))
                .build());

        mockMvc.perform(get("/api/suppliers/SUP-001/outstanding-balance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalPostedAmount").value(100.0))
            .andExpect(jsonPath("$.data.paidAmount").value(40.0))
            .andExpect(jsonPath("$.data.outstandingAmount").value(60.0));
    }

    @Test
    void updateAndActivationUseSeparateContracts() throws Exception {
        when(supplierService.updateSupplier(eq("SUP-001"), any())).thenReturn(response(true));
        when(supplierService.setSupplierActive("SUP-001", false)).thenReturn(response(false));

        mockMvc.perform(put("/api/suppliers/SUP-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contactNumber\":\"021-999\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("SUP-001"));

        mockMvc.perform(patch("/api/suppliers/SUP-001/activation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void deleteDelegatesToFinancialHistoryGuardedService() throws Exception {
        mockMvc.perform(delete("/api/suppliers/SUP-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));

        verify(supplierService).deleteSupplier("SUP-001");
    }

    private SupplierResponse response(boolean active) {
        return SupplierResponse.builder()
            .code("SUP-001")
            .name("Bloom Textile")
            .contactNumber("021-555")
            .address("Jakarta")
            .active(active)
            .build();
    }
}
