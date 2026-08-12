package com.bloom.app.service;

import com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest;
import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StockMovementQueryService {
    Page<StockMovementResponse> filterMovements(FilterStockMovementRequest request, Pageable pageable);

    Page<ItemAuditLogResponse> getItemAuditLogs(String sku, Pageable pageable);
}
