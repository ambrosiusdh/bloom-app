package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptItemResponse;
import com.bloom.app.domain.model.GoodsReceiptItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { ItemMapper.class })
public interface GoodsReceiptItemMapper {
    @Mapping(target = "item", source = "item")
    GoodsReceiptItemResponse toResponse(GoodsReceiptItem goodsReceiptItem);
}
