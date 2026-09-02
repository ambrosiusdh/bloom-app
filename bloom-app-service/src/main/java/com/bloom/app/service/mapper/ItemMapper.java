package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.request.item.CreateItemRequest;
import com.bloom.app.api.dto.request.item.UpdateItemRequest;
import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.model.Item;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = ItemCategoryMapper.class
)
public interface ItemMapper {
    @Mapping(target = "category", source = "category")
    @Mapping(target = "hasStockMovements", constant = "false")
    @Mapping(target = "baseUnitOfMeasureLocked", constant = "false")
    @Mapping(target = "fractionalQuantityAllowedLocked", constant = "false")
    ItemResponse itemToItemResponse(Item item);

    @Mapping(target = "category", source = "item.category")
    @Mapping(target = "hasStockMovements", source = "hasStockMovements")
    @Mapping(target = "baseUnitOfMeasureLocked", source = "hasStockMovements")
    @Mapping(target = "fractionalQuantityAllowedLocked", source = "hasStockMovements")
    ItemResponse itemToItemResponse(Item item, boolean hasStockMovements);

    @Mapping(target = "active", expression = "java(true)")
    @Mapping(target = "stockStore", ignore = true)
    @Mapping(target = "stockWarehouse", ignore = true)
    Item createRequestToEntity(CreateItemRequest request);

    @Mapping(target = "stockStore", ignore = true)
    @Mapping(target = "stockWarehouse", ignore = true)
    void updateRequestToEntity(UpdateItemRequest request, @MappingTarget Item item);

}
