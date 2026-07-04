package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.item.CreateItemRequest;
import com.bloom.app.api.dto.request.item.FilterItemRequest;
import com.bloom.app.api.dto.request.item.UpdateItemRequest;
import com.bloom.app.api.dto.response.item.ItemResponse;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.ItemCategory;
import com.bloom.app.domain.model.ItemCategoryCounter;
import com.bloom.app.persistence.repository.ItemCategoryCounterRepository;
import com.bloom.app.persistence.repository.ItemCategoryRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.service.ItemService;
import com.bloom.app.service.mapper.ItemMapper;
import com.bloom.app.service.specification.ItemSpecification;
import com.bloom.app.service.util.PdfGeneratorUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {
    private final ItemRepository itemRepository;
    private final ItemCategoryRepository itemCategoryRepository;
    private final ItemCategoryCounterRepository itemCategoryCounterRepository;
    private final ItemMapper itemMapper;
    private final PdfGeneratorUtil pdfGeneratorUtil;

    @Override
    @Transactional
    public ItemResponse createItem(CreateItemRequest request) {
        log.debug("ItemService createItem using request: {}", request);
        ItemCategory itemCategory = itemCategoryRepository.findByCode(request.getCategoryCode())
            .orElseThrow(() -> new ResponseStatusException(
                ErrorCode.ITEM_CATEGORY_NOT_FOUND.getStatus(), ErrorCode.ITEM_CATEGORY_NOT_FOUND.getMessage()
            ));

        String sku;
        if (request.getSku() == null) {
            sku = generateSku(itemCategory);
        } else {
            sku = request.getSku();
        }

        Item item = itemMapper.createRequestToEntity(request);
        item.setCategory(itemCategory);
        item.setSku(sku);
        return itemMapper.itemToItemResponse(itemRepository.save(item));
    }

    @Override
    @Transactional
    public ItemResponse updateItem(String sku, UpdateItemRequest request) {
        log.debug("ItemService updateItem using request: {}", request);
        Item item = itemRepository.findItemBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        itemMapper.updateRequestToEntity(request, item);
        return itemMapper.itemToItemResponse(itemRepository.save(item));
    }

    @Override
    @Transactional
    public void deactivateItem(String sku) {
        log.debug("ItemService deactivateItem using request: {}", sku);
        Item item = itemRepository.findItemBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
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
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
    }

    @Transactional
    @Override
    public String generateSku(ItemCategory itemCategory) {
        ItemCategoryCounter itemCategoryCounter = itemCategoryCounterRepository.findByCategory(itemCategory)
            .orElseGet(() -> ItemCategoryCounter.builder()
                .category(itemCategory)
                .lastSequence(0)
                .build()
            );
        long nextSequence = itemCategoryCounter.getLastSequence() + 1;
        String sku = String.format("%s-%05d", itemCategory.getCode(), nextSequence);

        itemCategoryCounter.setLastSequence(nextSequence);
        itemCategoryCounterRepository.save(itemCategoryCounter);
        return sku;
    }

    @Override
    public byte[] generateSingleBarcodePdf(String sku) {
        log.debug("ItemService generateSingleBarcodePdf using sku: {}", sku);
        Item item = itemRepository.findItemBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        return pdfGeneratorUtil.generateBarcodeLayoutPdf(List.of(item));
    }

    @Override
    public byte[] generateBulkBarcodePdf(List<String> skus) {
        log.debug("ItemService generateBulkBarcodePdf using skus: {}", skus);
        if (skus == null || skus.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU list cannot be empty");
        }

        List<Item> items = itemRepository.findBySkuIn(skus);
        if (items.isEmpty()) {
            throw new ResourceNotFoundException("No items found for the provided SKUs");
        }

        return pdfGeneratorUtil.generateBarcodeLayoutPdf(items);
    }
}
