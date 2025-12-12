package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.request.stockadjustment.CreateStockAdjustmentRequest;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import com.bloom.app.domain.model.StockAdjustment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { StockAdjustmentItemMapper.class })
public interface StockAdjustmentMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "stockAdjustmentCode", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "items", ignore = true)
    StockAdjustment createRequestToEntity(CreateStockAdjustmentRequest request);

    StockAdjustmentResponse toResponse(StockAdjustment stockAdjustment);
}
