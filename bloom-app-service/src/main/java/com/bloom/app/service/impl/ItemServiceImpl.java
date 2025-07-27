package com.bloom.app.service.impl;

import com.bloom.app.domain.dto.request.item.CreateItemRequest;
import com.bloom.app.domain.dto.request.item.FilterItemRequest;
import com.bloom.app.domain.dto.request.item.UpdateItemRequest;
import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.domain.model.Item;
import com.bloom.app.repository.ItemRepository;
import com.bloom.app.service.ItemService;
import com.bloom.app.service.mapper.ItemMapper;
import com.bloom.app.service.specification.ItemSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
        if (itemRepository.existsBySku(request.getSku())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item sku already exists");
        }

        Item item = itemMapper.createRequestToEntity(request);
        item.setActive(true);
        return itemMapper.itemToItemResponse(itemRepository.save(item));
    }

    @Override
    public ItemResponse updateItem(String sku, UpdateItemRequest request) {
        log.debug("ItemService updateItem using request: {}", request);
        Item item = itemRepository.findItemBySku(sku)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found"));
        itemMapper.updateRequestToEntity(request, item);
        return itemMapper.itemToItemResponse(itemRepository.save(item));
    }

    @Override
    public void deactivateItem(String sku) {
        log.debug("ItemService deactivateItem using request: {}", sku);
        Item item = itemRepository.findItemBySku(sku)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found"));
        item.setActive(false);
        itemRepository.save(item);
    }

    @Override
    public Page<ItemResponse> filterItems(FilterItemRequest request, Pageable pageable) {
        Page<Item> itemPage = itemRepository.findAll(ItemSpecification.filter(request), pageable);

        List<ItemResponse> itemResponseList = itemPage.getContent()
            .stream()
            .map(itemMapper::itemToItemResponse)
            .toList();

        return new PageImpl<>(itemResponseList, pageable, itemPage.getTotalElements());
    }

    @Override
    public ItemResponse getItemDetails(String sku) {
        log.debug("ItemService getItemDetails using request: {}", sku);
        return itemRepository.findItemBySku(sku)
            .map(itemMapper::itemToItemResponse)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found"));
    }
}
