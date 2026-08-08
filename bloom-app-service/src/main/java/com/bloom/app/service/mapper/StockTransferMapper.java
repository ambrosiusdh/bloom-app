package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.request.stocktransfer.CreateStockTransferRequest;
import com.bloom.app.api.dto.response.stocktransfer.StockTransferResponse;
import com.bloom.app.domain.model.StockTransfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = StockTransferLineMapper.class)
public interface StockTransferMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "requestKey", ignore = true)
    @Mapping(target = "requestHash", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lines", ignore = true)
    StockTransfer createRequestToEntity(CreateStockTransferRequest request);

    StockTransferResponse toResponse(StockTransfer transfer);
}
