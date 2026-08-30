package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.domain.model.GoodsReceipt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { GoodsReceiptItemMapper.class })
public interface GoodsReceiptMapper {
    @Mapping(source = "supplier.id", target = "supplierId")
    @Mapping(source = "supplier.code", target = "supplierCode")
    @Mapping(source = "supplierNameSnapshot", target = "supplierName")
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "outstandingAmount", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    GoodsReceiptResponse toResponse(GoodsReceipt goodsReceipt);
}
