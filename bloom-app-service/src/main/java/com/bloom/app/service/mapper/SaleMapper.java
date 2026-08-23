package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.response.sale.SaleResponse;
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
    @Mapping(target = "sessionId", source = "cashSession.id")
    SaleResponse saleToResponse(Sale sale);

    @Mapping(target = "items", ignore = true)
    @Mapping(target = "cashSession", ignore = true)
    @Mapping(target = "checkoutIdempotencyKey", ignore = true)
    @Mapping(target = "checkoutRequestHash", ignore = true)
    @Mapping(target = "changeAmount", ignore = true)
    Sale createRequestToEntity(CreateSaleRequest request);
}
