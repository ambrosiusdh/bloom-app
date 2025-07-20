package com.bloom.app.api.controller;

import com.bloom.app.domain.dto.request.item.CreateItemRequest;
import com.bloom.app.domain.dto.request.item.UpdateItemRequest;
import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {
    private final ItemService itemService;

    @GetMapping
    public ResponseEntity<List<ItemResponse>> getAllItems() {
        return ResponseEntity.ok(itemService.getAllItems());
    }

    @PostMapping
    public ResponseEntity<ItemResponse> createItem(@RequestBody CreateItemRequest request) {
        ItemResponse response = itemService.createItem(request);

        return ResponseEntity.ok(response);
    }

    @PutMapping(path = "/{sku}")
    public ResponseEntity<ItemResponse> createItem(
            @PathVariable String sku,
            @RequestBody UpdateItemRequest request
    ) {
        ItemResponse response = itemService.updateItem(sku, request);

        return ResponseEntity.ok(response);
    }
}
