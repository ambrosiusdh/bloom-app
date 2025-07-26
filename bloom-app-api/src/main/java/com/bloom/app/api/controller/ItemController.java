package com.bloom.app.api.controller;

import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.dto.request.item.CreateItemRequest;
import com.bloom.app.domain.dto.request.item.FilterItemRequest;
import com.bloom.app.domain.dto.request.item.UpdateItemRequest;
import com.bloom.app.domain.dto.response.ApiResponse;
import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {
    private final ItemService itemService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ItemResponse>>> filterItems(
        @ModelAttribute FilterItemRequest filterItemRequest,
        Pageable pageable
    ) {
        Page<ItemResponse> response = itemService.filterItems(filterItemRequest, pageable);
        return ResponseHelper.ok(response);
    }

    @GetMapping(path = "/{sku}")
    public ResponseEntity<ApiResponse<ItemResponse>> getItemDetails(
        @PathVariable String sku
    ) {
        ItemResponse response = itemService.getItemDetails(sku);
        return ResponseHelper.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ItemResponse>> createItem(@Valid @RequestBody CreateItemRequest request) {
        ItemResponse response = itemService.createItem(request);
        return ResponseHelper.ok(response);
    }

    @PutMapping(path = "/{sku}")
    public ResponseEntity<ApiResponse<ItemResponse>> updateItem(
            @PathVariable String sku,
            @Valid @RequestBody UpdateItemRequest request
    ) {
        ItemResponse response = itemService.updateItem(sku, request);

        return ResponseHelper.ok(response);
    }

    @PatchMapping(path = "/{sku}")
    public ResponseEntity<ApiResponse<Boolean>> deactivateItem(
        @PathVariable String sku
    ) {
        itemService.deactivateItem(sku);
        return ResponseHelper.ok(Boolean.TRUE);
    }
}
