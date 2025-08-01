package com.bloom.app.api.controller;

import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.dto.request.itemcategory.CreateItemCategoryRequest;
import com.bloom.app.domain.dto.request.itemcategory.FilterItemCategoryRequest;
import com.bloom.app.domain.dto.request.itemcategory.UpdateItemCategoryRequest;
import com.bloom.app.domain.dto.response.ApiResponse;
import com.bloom.app.domain.dto.response.itemcategory.ItemCategoryResponse;
import com.bloom.app.service.ItemCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/item-categories")
@RequiredArgsConstructor
public class ItemCategoryController {
    private final ItemCategoryService itemCategoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ItemCategoryResponse>>> filterItemCategory(
        FilterItemCategoryRequest request,
        Pageable pageable) {
        Page<ItemCategoryResponse> response = itemCategoryService.filterItemCategory(request, PagingHelper.toPageRequest(pageable));
        return ResponseHelper.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ItemCategoryResponse>> createItemCategory(@Valid @RequestBody CreateItemCategoryRequest request) {
        ItemCategoryResponse response = itemCategoryService.createItemCategory(request);
        return ResponseHelper.ok(response);
    }

    @PutMapping(path = "/{code}")
    public ResponseEntity<ApiResponse<ItemCategoryResponse>> updateItemCategory(
        UpdateItemCategoryRequest request,
        @PathVariable String code) {
        ItemCategoryResponse response = itemCategoryService.updateItemCategory(request, code);
        return ResponseHelper.ok(response);
    }

    @PatchMapping(path = "/{code}")
    public ResponseEntity<ApiResponse<Boolean>> deactivateItemCategory(@PathVariable String code) {
        Boolean response = itemCategoryService.deactivateItemCategory(code);
        return ResponseHelper.ok(response);
    }
}
