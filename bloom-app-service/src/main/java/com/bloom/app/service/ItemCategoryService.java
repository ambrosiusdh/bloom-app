package com.bloom.app.service;

import com.bloom.app.domain.dto.request.itemcategory.CreateItemCategoryRequest;
import com.bloom.app.domain.dto.request.itemcategory.FilterItemCategoryRequest;
import com.bloom.app.domain.dto.request.itemcategory.UpdateItemCategoryRequest;
import com.bloom.app.domain.dto.response.itemcategory.ItemCategoryItemCountResponse;
import com.bloom.app.domain.dto.response.itemcategory.ItemCategoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ItemCategoryService {
    ItemCategoryResponse createItemCategory(CreateItemCategoryRequest request);
    Page<ItemCategoryResponse> filterItemCategory(FilterItemCategoryRequest request, Pageable pageable);
    ItemCategoryResponse getItemCategoryDetails(String code);
    ItemCategoryResponse updateItemCategory(UpdateItemCategoryRequest request, String code);
    Boolean deactivateItemCategory(String code);
    ItemCategoryItemCountResponse getItemCategoryItemCount(String code);
}
