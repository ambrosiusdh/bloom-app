package com.bloom.app.web.controller;

import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.service.StockMovementQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemStockHistoryController {
    private final StockMovementQueryService stockMovementQueryService;

    @GetMapping("/{sku}/audit-log")
    @Operation(summary = "Get item audit logs",
        description = "Retrieve item stock history from the authoritative stock movement ledger.")
    public ResponseEntity<ApiResponse<Page<ItemAuditLogResponse>>> getItemAuditLogs(
            @Parameter(description = "SKU of the item") @PathVariable String sku,
            Pageable pageable) {
        Page<ItemAuditLogResponse> response = stockMovementQueryService.getItemAuditLogs(
            sku, PagingHelper.toPageRequest(pageable));
        return ResponseHelper.ok(response);
    }
}
