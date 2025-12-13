package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentItemResponse;
import com.bloom.app.domain.model.StockAdjustmentItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { ItemMapper.class })
public interface StockAdjustmentItemMapper {
    @Mapping(target = "item", source = "item")
    StockAdjustmentItemResponse toResponse(StockAdjustmentItem stockAdjustmentItem);
}
