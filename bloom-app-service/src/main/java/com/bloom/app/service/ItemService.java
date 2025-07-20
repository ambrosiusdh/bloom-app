package com.bloom.app.service;

import com.bloom.app.domain.dto.request.item.CreateItemRequest;
import com.bloom.app.domain.dto.request.item.UpdateItemRequest;
import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.domain.model.Item;

import java.util.List;

public interface ItemService {
    ItemResponse createItem(CreateItemRequest request);

    ItemResponse updateItem(String sku, UpdateItemRequest request);

    void deleteItem(Item item);

    List<ItemResponse> getAllItems();
    Item getItemByItemCode(String itemCode);
}
