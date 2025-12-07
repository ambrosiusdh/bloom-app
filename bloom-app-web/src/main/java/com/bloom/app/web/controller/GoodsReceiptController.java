package com.bloom.app.web.controller;

import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.domain.dto.response.ApiResponse;
import com.bloom.app.domain.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.service.GoodsReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goods-receipts")
@RequiredArgsConstructor
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    @PostMapping
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> createGoodsReceipt(
            @Validated @RequestBody CreateGoodsReceiptRequest request) {
        GoodsReceiptResponse response = goodsReceiptService.createGoodsReceipt(request);
        return ResponseHelper.created("Goods Receipt created successfully", response);
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> getGoodsReceiptDetails(@PathVariable String code) {
        GoodsReceiptResponse response = goodsReceiptService.getGoodsReceiptDetails(code);
        return ResponseHelper.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<GoodsReceiptResponse>>> getGoodsReceipts(Pageable pageable) {
        Page<GoodsReceiptResponse> page = goodsReceiptService.getGoodsReceipts(pageable);
        return ResponseHelper.ok(page);
    }
}
