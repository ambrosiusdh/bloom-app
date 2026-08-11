package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogControllerTest {
    private AuditLogService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AuditLogService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AuditLogController(service))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void retainsLegacyItemAuditEndpointAndResponseShape() throws Exception {
        when(service.getItemAuditLogs(eq("SKU-7"), any()))
            .thenReturn(new PageImpl<>(List.of(ItemAuditLogResponse.builder()
                .id(7L).referenceNo("SALE-7").build()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/items/SKU-7/audit-log").param("page", "1").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(7))
            .andExpect(jsonPath("$.data.content[0].referenceNo").value("SALE-7"));

        verify(service).getItemAuditLogs(eq("SKU-7"),
            org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageNumber() == 0));
    }
}
