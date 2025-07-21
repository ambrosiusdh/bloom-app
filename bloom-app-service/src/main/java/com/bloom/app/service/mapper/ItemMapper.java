package com.bloom.app.service.mapper;

import com.bloom.app.domain.dto.request.item.CreateItemRequest;
import com.bloom.app.domain.dto.request.item.UpdateItemRequest;
import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.domain.model.Item;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ItemMapper {
    ItemResponse itemToItemResponse(Item item);

    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    Item createItemRequestToEntity(CreateItemRequest request);

    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    void updateItemRequestToEntity(UpdateItemRequest request, @MappingTarget Item item);
}
