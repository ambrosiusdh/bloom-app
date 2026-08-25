package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import com.bloom.app.domain.model.StockAdjustment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = { StockAdjustmentItemMapper.class })
public interface StockAdjustmentMapper {
    StockAdjustmentResponse toResponse(StockAdjustment stockAdjustment);
}
