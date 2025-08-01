package com.bloom.app.service.impl;

import com.bloom.app.domain.dto.request.itemcategory.CreateItemCategoryRequest;
import com.bloom.app.domain.dto.request.itemcategory.FilterItemCategoryRequest;
import com.bloom.app.domain.dto.request.itemcategory.UpdateItemCategoryRequest;
import com.bloom.app.domain.dto.response.itemcategory.ItemCategoryResponse;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.model.ItemCategory;
import com.bloom.app.repository.ItemCategoryRepository;
import com.bloom.app.repository.ItemRepository;
import com.bloom.app.service.ItemCategoryService;
import com.bloom.app.service.mapper.ItemCategoryMapper;
import com.bloom.app.service.specification.ItemCategorySpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemCategoryServiceImpl implements ItemCategoryService {
    private final ItemCategoryRepository itemCategoryRepository;
    private final ItemRepository itemRepository;
    private final ItemCategoryMapper itemCategoryMapper;

    @Override
    public ItemCategoryResponse createItemCategory(CreateItemCategoryRequest request) {
        log.debug("ItemCategoryService createItemCategory with request: {}", request);

        if (itemCategoryRepository.existsByCode(request.getCode())) {
            throw new ResponseStatusException(ErrorCode.ITEM_CATEGORY_ALREADY_EXIST.getStatus(), ErrorCode.ITEM_CATEGORY_ALREADY_EXIST.getMessage());
        }

        ItemCategory itemCategory = itemCategoryMapper.createRequestToEntity(request);
        return itemCategoryMapper.entityToResponse(itemCategoryRepository.save(itemCategory));
    }

    @Override
    public Page<ItemCategoryResponse> filterItemCategory(FilterItemCategoryRequest request, Pageable pageable) {
        log.debug("ItemCategoryService filterItemCategory with request: {}", request);

        Page<ItemCategory> itemCategoryPage = itemCategoryRepository.findAll(ItemCategorySpecification.filter(request), pageable);
        List<ItemCategoryResponse> itemCategoryResponseList = itemCategoryPage.getContent()
            .stream()
            .map(itemCategoryMapper::entityToResponse)
            .toList();

        return new PageImpl<>(itemCategoryResponseList, pageable, itemCategoryPage.getTotalElements());
    }

    @Override
    public ItemCategoryResponse updateItemCategory(UpdateItemCategoryRequest request,  String code) {
        log.debug("ItemCategoryService updateItemCategory with request: {}", request);

        ItemCategory itemCategory = itemCategoryRepository.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(ErrorCode.ITEM_CATEGORY_NOT_FOUND.getStatus(), ErrorCode.ITEM_CATEGORY_NOT_FOUND.getMessage()));
        itemCategoryMapper.copyUpdateRequestToEntity(request, itemCategory);
        return itemCategoryMapper.entityToResponse(itemCategoryRepository.save(itemCategory));
    }

    @Override
    public Boolean deactivateItemCategory(String code) {
        log.debug("ItemCategoryService deactivateItemCategory with code: {}", code);
        ItemCategory itemCategory = itemCategoryRepository.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(ErrorCode.ITEM_CATEGORY_NOT_FOUND.getStatus(), ErrorCode.ITEM_CATEGORY_NOT_FOUND.getMessage()));
        if (itemRepository.countByCategoryAndActiveTrue(itemCategory) > 0) {
            throw new ResponseStatusException(ErrorCode.ITEM_CATEGORY_HAS_ACTIVE_ITEM.getStatus(), ErrorCode.ITEM_CATEGORY_HAS_ACTIVE_ITEM.getMessage());
        }

        itemCategory.setActive(false);
        itemCategoryRepository.save(itemCategory);
        return Boolean.TRUE;
    }
}
