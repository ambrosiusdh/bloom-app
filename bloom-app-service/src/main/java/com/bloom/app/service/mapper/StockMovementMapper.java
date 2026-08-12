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
    StockMovementResponse toResponse(StockMovement stockMovement);

    @Mapping(target = "source", source = "sourceType")
    @Mapping(target = "qty", source = "quantity")
    @Mapping(target = "createdDate", source = "createdAt")
    ItemAuditLogResponse toAuditResponse(StockMovementResponse stockMovement);
}
