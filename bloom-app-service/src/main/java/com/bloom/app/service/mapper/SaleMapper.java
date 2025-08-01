package com.bloom.app.service.mapper;

import com.bloom.app.domain.dto.request.sale.CreateSaleRequest;
import com.bloom.app.domain.dto.response.sale.SaleResponse;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.service.SaleItemMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = SaleItemMapper.class
)
public interface SaleMapper {
    @Mapping(target = "saleItems", source = "items")
    SaleResponse saleToResponse(Sale sale);

    @Mapping(target = "items", ignore = true)
    Sale createRequestToEntity(CreateSaleRequest request);
}
