package com.bloom.app.service.impl;

import com.bloom.app.domain.dto.request.item.CreateItemRequest;
import com.bloom.app.domain.dto.request.item.UpdateItemRequest;
import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.domain.model.Item;
import com.bloom.app.repository.ItemRepository;
import com.bloom.app.service.ItemService;
import com.bloom.app.service.mapper.ItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemMapper itemMapper;

    @Override
    public ItemResponse createItem(CreateItemRequest request) {
        log.debug("ItemService createItem using request: {}", request);
        Item item = itemMapper.createItemRequestToEntity(request);

        return itemMapper.itemToItemResponse(itemRepository.save(item));
    }

    @Override
    public ItemResponse updateItem(String sku, UpdateItemRequest request) {
        return null;
    }

    @Override
    public void deleteItem(Item item) {

    }

    @Override
    public List<ItemResponse> getAllItems() {
        return itemRepository.findAll().stream()
                .map(itemMapper::itemToItemResponse)
                .toList();
    }

    @Override
    public Item getItemByItemCode(String itemCode) {
        return null;
    }

    public Item getItemById(long id) {
        return itemRepository.findById(id).orElse(null);
    }
}
