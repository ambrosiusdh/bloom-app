package com.bloom.app.service;

import com.bloom.app.domain.dto.request.item.CreateItemRequest;
import com.bloom.app.domain.dto.request.item.FilterItemRequest;
import com.bloom.app.domain.dto.request.item.UpdateItemRequest;
import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.domain.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ItemService {
    ItemResponse createItem(CreateItemRequest request);

    ItemResponse updateItem(String sku, UpdateItemRequest request);

    void deactivateItem(String sku);

    Page<ItemResponse> filterItems(FilterItemRequest request, Pageable pageable);

    ItemResponse getItemDetails(String sku);
}
