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
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "supplierNameSnapshot", ignore = true)
    @Mapping(target = "createIdempotencyKey", ignore = true)
    @Mapping(target = "createRequestHash", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "cancelledBy", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    GoodsReceipt createRequestToEntity(CreateGoodsReceiptRequest request);

    @Mapping(source = "supplier.id", target = "supplierId")
    @Mapping(source = "supplier.code", target = "supplierCode")
    @Mapping(source = "supplierNameSnapshot", target = "supplierName")
    GoodsReceiptResponse toResponse(GoodsReceipt goodsReceipt);
}
