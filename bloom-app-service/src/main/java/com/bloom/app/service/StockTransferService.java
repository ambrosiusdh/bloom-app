package com.bloom.app.service;

import com.bloom.app.api.dto.request.stocktransfer.CreateStockTransferRequest;
import com.bloom.app.api.dto.request.stocktransfer.FilterStockTransferRequest;
import com.bloom.app.api.dto.response.stocktransfer.StockTransferResponse;
import com.bloom.app.api.dto.response.stocktransfer.StockTransferSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StockTransferService {
    StockTransferResponse createStockTransfer(String requestKey, CreateStockTransferRequest request);

    StockTransferResponse getStockTransferDetails(String code);

    Page<StockTransferSummaryResponse> listStockTransfers(
        FilterStockTransferRequest request, Pageable pageable);
}
