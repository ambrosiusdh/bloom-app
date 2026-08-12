package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.auditlog.FilterAuditLogRequest;
import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.service.StockMovementQueryService;
import com.bloom.app.service.mapper.StockMovementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceImplTest {
    private final StockMovementQueryService queryService = mock(StockMovementQueryService.class);
    private final StockMovementMapper mapper = mock(StockMovementMapper.class);
    private final AuditLogServiceImpl service = new AuditLogServiceImpl(queryService, mapper);

    @ParameterizedTest
    @EnumSource(StockAdjustmentActionType.class)
    void adaptsEveryLegacyAdjustmentActionExactly(StockAdjustmentActionType actionType) {
        StockMovementResponse movement = StockMovementResponse.builder().id(3L).build();
        ItemAuditLogResponse audit = ItemAuditLogResponse.builder().id(3L).build();
        when(queryService.filterMovements(any(), any()))
            .thenReturn(new PageImpl<>(List.of(movement)));
        when(mapper.toAuditResponse(movement)).thenReturn(audit);

        var result = service.filterAuditLogs(FilterAuditLogRequest.builder()
            .itemSku("SKU-3")
            .referenceNo("SALE-3")
            .actionType(actionType)
            .build(), PageRequest.of(0, 20));

        assertThat(result.getContent()).containsExactly(audit);
        ArgumentCaptor<com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest> captor =
            ArgumentCaptor.forClass(
                com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest.class);
        verify(queryService).filterMovements(captor.capture(), any());
        assertThat(captor.getValue().getItemSku()).isEqualTo("SKU-3");
        assertThat(captor.getValue().getReference()).isEqualTo("SALE-3");
        assertThat(captor.getValue().getSourceType()).isEqualTo(MovementSourceType.STOCK_ADJUSTMENT);
        assertThat(captor.getValue().getAdjustmentActionType())
            .isEqualTo(actionType);
    }

    @Test
    void translatesEveryLegacyAuditSortProperty() {
        when(queryService.filterMovements(any(), any()))
            .thenReturn(new PageImpl<>(List.of()));
        Pageable legacyPageable = PageRequest.of(2, 15, Sort.by(
            Sort.Order.desc("createdDate"),
            Sort.Order.asc("qty"),
            Sort.Order.asc("source"),
            Sort.Order.desc("referenceNo"),
            Sort.Order.asc("qtyBefore"),
            Sort.Order.desc("qtyAfter"),
            Sort.Order.asc("createdBy"),
            Sort.Order.asc("item")
        ));

        service.filterAuditLogs(new FilterAuditLogRequest(), legacyPageable);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).filterMovements(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(15);
        assertThat(pageableCaptor.getValue().getSort())
            .extracting(Sort.Order::getProperty)
            .containsExactly(
                "createdAt", "quantity", "sourceType", "referenceNo",
                "qtyBefore", "qtyAfter", "createdBy", "itemId");
    }
}
