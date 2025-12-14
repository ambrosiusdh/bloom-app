package com.bloom.app.web.controller;

import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.api.dto.request.goodsreceipt.FilterGoodsReceiptRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.service.GoodsReceiptService;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Create Goods Receipt", description = "Create a new goods receipt.")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> createGoodsReceipt(
        @Validated @RequestBody CreateGoodsReceiptRequest request
    ) {
        GoodsReceiptResponse response = goodsReceiptService.createGoodsReceipt(request);
        return ResponseHelper.created("Goods Receipt created successfully", response);
    }

    @GetMapping("/details")
    @Operation(summary = "Get Details Goods Receipt", description = "Get details of a specific goods receipt by code.")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> getGoodsReceiptDetails(@RequestParam String code) {
        GoodsReceiptResponse response = goodsReceiptService.getGoodsReceiptDetails(code);
        return ResponseHelper.ok(response);
    }

    @GetMapping
    @Operation(summary = "Filter Goods Receipt", description = "Filter and paginate goods receipts.")
    public ResponseEntity<ApiResponse<Page<GoodsReceiptResponse>>> filterGoodsReceipts(
        @ModelAttribute FilterGoodsReceiptRequest request,
        Pageable pageable
    ) {
        Page<GoodsReceiptResponse> page = goodsReceiptService.filterGoodsReceipts(request, PagingHelper.toPageRequest(pageable));
        return ResponseHelper.ok(page);
    }
}
