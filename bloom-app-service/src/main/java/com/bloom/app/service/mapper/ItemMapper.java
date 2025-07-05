package com.bloom.app.service.mapper;

import com.bloom.app.dto.request.item.CreateItemRequest;
import com.bloom.app.dto.response.item.ItemResponse;
import com.bloom.app.model.Item;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ItemMapper {
    ItemResponse itemToItemResponse(Item item);
    Item createItemRequestToEntity(CreateItemRequest request);
}
