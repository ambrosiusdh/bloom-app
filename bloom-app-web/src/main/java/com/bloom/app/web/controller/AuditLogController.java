package com.bloom.app.web.controller;

import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.api.dto.request.auditlog.FilterAuditLogRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.service.AuditLogService;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogService auditLogService;

    @GetMapping("/audit-log")
    @Operation(summary = "Filter Audit Logs", description = "Retrieve a paginated list of audit logs based on filters.")
    public ResponseEntity<ApiResponse<Page<ItemAuditLogResponse>>> filterAuditLogs(
            FilterAuditLogRequest request,
            Pageable pageable) {
        Page<ItemAuditLogResponse> response = auditLogService.filterAuditLogs(request,
                PagingHelper.toPageRequest(pageable));
        return ResponseHelper.ok(response);
    }

    @GetMapping("/items/{sku}/audit-log")
    @Operation(summary = "Get Item Audit Logs", description = "Retrieve audit logs for a specific item with pagination.")
    public ResponseEntity<ApiResponse<Page<ItemAuditLogResponse>>> getItemAuditLogs(
            @Parameter(description = "SKU of the item") @PathVariable String sku,
            Pageable pageable) {
        Page<ItemAuditLogResponse> response = auditLogService.getItemAuditLogs(sku,
                PagingHelper.toPageRequest(pageable));
        return ResponseHelper.ok(response);
    }
}
