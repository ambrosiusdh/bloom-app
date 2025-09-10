package com.bloom.app.api.controller;

import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.dto.request.item.CreateItemRequest;
import com.bloom.app.domain.dto.request.item.FilterItemRequest;
import com.bloom.app.domain.dto.request.item.UpdateItemRequest;
import com.bloom.app.domain.dto.response.ApiResponse;
import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {
    private final ItemService itemService;

    @GetMapping
    @Operation(
        summary = "Filter and list items",
        description = "Retrieve a paginated list of items based on filter criteria (category, name, stock, etc.)."
    )
    public ResponseEntity<ApiResponse<Page<ItemResponse>>> filterItems(
        @Parameter(description = "Filter parameters for item search") FilterItemRequest request,
        @Parameter(description = "Pagination and sorting information") Pageable pageable
    ) {
        Page<ItemResponse> response = itemService.filterItems(request, PagingHelper.toPageRequest(pageable));
        return ResponseHelper.ok(response);
    }

    @GetMapping(path = "/{sku}")
    @Operation(
        summary = "Get item details",
        description = "Fetch detailed information about a single item using its SKU."
    )
    public ResponseEntity<ApiResponse<ItemResponse>> getItemDetails(
        @Parameter(description = "The unique SKU of the item") @PathVariable String sku
    ) {
        ItemResponse response = itemService.getItemDetails(sku);
        return ResponseHelper.ok(response);
    }

    @PostMapping
    @Operation(
        summary = "Create a new item",
        description = "Add a new item into the system with the provided details."
    )
    public ResponseEntity<ApiResponse<ItemResponse>> createItem(
        @Valid @RequestBody CreateItemRequest request
    ) {
        ItemResponse response = itemService.createItem(request);
        return ResponseHelper.ok(response);
    }

    @PutMapping(path = "/{sku}")
    @Operation(
        summary = "Update an item",
        description = "Update the details of an existing item identified by its SKU."
    )
    public ResponseEntity<ApiResponse<ItemResponse>> updateItem(
        @Parameter(description = "The SKU of the item to update") @PathVariable String sku,
        @Valid @RequestBody UpdateItemRequest request
    ) {
        ItemResponse response = itemService.updateItem(sku, request);
        return ResponseHelper.ok(response);
    }

    @PatchMapping(path = "/{sku}")
    @Operation(
        summary = "Deactivate an item",
        description = "Mark an item as inactive using its SKU without deleting it from the system."
    )
    public ResponseEntity<ApiResponse<Boolean>> deactivateItem(
        @Parameter(description = "The SKU of the item to deactivate") @PathVariable String sku
    ) {
        itemService.deactivateItem(sku);
        return ResponseHelper.ok(Boolean.TRUE);
    }
}
