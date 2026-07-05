package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.domain.model.GoodsReceipt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { GoodsReceiptItemMapper.class })
public interface GoodsReceiptMapper {
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    GoodsReceipt createRequestToEntity(CreateGoodsReceiptRequest request);

    @Mapping(source = "supplier.code", target = "supplierCode")
    @Mapping(source = "supplier.name", target = "supplierName")
    GoodsReceiptResponse toResponse(GoodsReceipt goodsReceipt);
}
