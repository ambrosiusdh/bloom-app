package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.auditlog.FilterAuditLogRequest;
import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.service.StockMovementQueryService;
import com.bloom.app.service.mapper.StockMovementMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void adaptsLegacyAuditFiltersAndResponseFromStockMovements() {
        StockMovementResponse movement = StockMovementResponse.builder().id(3L).build();
        ItemAuditLogResponse audit = ItemAuditLogResponse.builder().id(3L).build();
        when(queryService.filterMovements(any(), any()))
            .thenReturn(new PageImpl<>(List.of(movement)));
        when(mapper.toAuditResponse(movement)).thenReturn(audit);

        var result = service.filterAuditLogs(FilterAuditLogRequest.builder()
            .itemSku("SKU-3")
            .referenceNo("SALE-3")
            .actionType(StockAdjustmentActionType.REMOVE)
            .build(), PageRequest.of(0, 20));

        assertThat(result.getContent()).containsExactly(audit);
        ArgumentCaptor<com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest> captor =
            ArgumentCaptor.forClass(
                com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest.class);
        verify(queryService).filterMovements(captor.capture(), any());
        assertThat(captor.getValue().getItemSku()).isEqualTo("SKU-3");
        assertThat(captor.getValue().getReference()).isEqualTo("SALE-3");
        assertThat(captor.getValue().getMovementType()).isEqualTo(MovementType.OUT);
    }
}
