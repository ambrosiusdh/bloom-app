package com.bloom.app.service;

import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bloom.app.api.dto.request.goodsreceipt.FilterGoodsReceiptRequest;

public interface GoodsReceiptService {
    GoodsReceiptResponse createGoodsReceipt(CreateGoodsReceiptRequest request);

    GoodsReceiptResponse getGoodsReceiptDetails(String code);

    Page<GoodsReceiptResponse> filterGoodsReceipts(FilterGoodsReceiptRequest request, Pageable pageable);
}
