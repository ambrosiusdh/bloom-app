package com.bloom.app.service;

import com.bloom.app.dto.request.item.CreateItemRequest;
import com.bloom.app.dto.response.item.ItemResponse;
import com.bloom.app.model.Item;

import java.util.List;

public interface ItemService {
    ItemResponse createItem(CreateItemRequest request);

    Item updateItem(Item item);

    void deleteItem(Item item);

    List<ItemResponse> getAllItems();
    Item getItemByItemCode(String itemCode);
}
