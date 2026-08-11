package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.auditlog.ItemAuditLogResponse;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.domain.model.StockMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ItemMapper.class)
public interface StockMovementMapper {
    @Mapping(target = "item", source = "product")
    @Mapping(target = "location", source = "stockLocation")
    @Mapping(target = "referenceNo", source = "displayReference")
    @Mapping(target = "adjustmentActionType", source = "effectiveAdjustmentActionType")
    StockMovementResponse toResponse(StockMovement stockMovement);

    @Mapping(target = "source", source = "sourceType")
    @Mapping(target = "qty", source = "quantity")
    @Mapping(target = "createdDate", source = "createdAt")
    @Mapping(target = "id", expression = "java(stockMovement.getLegacyAuditLogId() != null "
        + "? stockMovement.getLegacyAuditLogId() : stockMovement.getId())")
    ItemAuditLogResponse toAuditResponse(StockMovementResponse stockMovement);
}
