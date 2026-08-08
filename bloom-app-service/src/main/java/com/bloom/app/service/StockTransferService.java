package com.bloom.app.service;

import com.bloom.app.api.dto.request.stocktransfer.CreateStockTransferRequest;
import com.bloom.app.api.dto.response.stocktransfer.StockTransferResponse;

public interface StockTransferService {
    StockTransferResponse createStockTransfer(String requestKey, CreateStockTransferRequest request);

    StockTransferResponse getStockTransferDetails(String code);
}
