package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.auditlog.FilterAuditLogRequest;
import com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest;
import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.service.AuditLogService;
import com.bloom.app.service.StockMovementQueryService;
import com.bloom.app.service.mapper.StockMovementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {
    private final StockMovementQueryService stockMovementQueryService;
    private final StockMovementMapper stockMovementMapper;

    @Override
    public Page<ItemAuditLogResponse> filterAuditLogs(FilterAuditLogRequest request, Pageable pageable) {
        log.debug("AuditLogService filterAuditLogs with request: {}", request);
        return stockMovementQueryService.filterMovements(toStockMovementFilter(request), pageable)
            .map(stockMovementMapper::toAuditResponse);
    }

    @Override
    public Page<ItemAuditLogResponse> getItemAuditLogs(String sku, Pageable pageable) {
        log.debug("AuditLogService getItemAuditLogs with sku: {}", sku);
        return stockMovementQueryService.filterMovements(
                FilterStockMovementRequest.builder().itemSku(sku).build(), pageable)
            .map(stockMovementMapper::toAuditResponse);
    }

    private FilterStockMovementRequest toStockMovementFilter(FilterAuditLogRequest request) {
        FilterStockMovementRequest.FilterStockMovementRequestBuilder builder =
            FilterStockMovementRequest.builder()
                .itemSku(request.getItemSku())
                .reference(request.getReferenceNo())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate());

        if (request.getActionType() != null) {
            switch (request.getActionType()) {
                case ADD -> builder.movementType(MovementType.IN);
                case REMOVE -> builder.movementType(MovementType.OUT);
                case CORRECTION -> builder.sourceType(MovementSourceType.STOCK_ADJUSTMENT);
            }
        }
        return builder.build();
    }
}
