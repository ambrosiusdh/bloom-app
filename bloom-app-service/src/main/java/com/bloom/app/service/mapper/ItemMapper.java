package com.bloom.app.service.mapper;

import com.bloom.app.domain.dto.request.item.CreateItemRequest;
import com.bloom.app.domain.dto.request.item.UpdateItemRequest;
import com.bloom.app.domain.dto.response.item.ItemResponse;
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
    ItemResponse itemToItemResponse(Item item);

    @Mapping(target = "active", expression = "java(true)")
    Item createRequestToEntity(CreateItemRequest request);

    void updateRequestToEntity(UpdateItemRequest request, @MappingTarget Item item);

}
