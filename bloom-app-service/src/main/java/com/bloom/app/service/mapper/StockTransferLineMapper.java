package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.stocktransfer.StockTransferLineResponse;
import com.bloom.app.domain.model.StockTransferLine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ItemMapper.class)
public interface StockTransferLineMapper {
    @Mapping(target = "item", source = "item")
    StockTransferLineResponse toResponse(StockTransferLine line);
}
