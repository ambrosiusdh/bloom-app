package com.bloom.app.service;

import com.bloom.app.domain.dto.response.saleitem.SaleItemResponse;
import com.bloom.app.domain.model.SaleItem;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface SaleItemMapper {
    SaleItemResponse entityToResponse(SaleItem saleItem);
}
