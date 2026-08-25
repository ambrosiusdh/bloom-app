package com.bloom.app.service;

import com.bloom.app.api.dto.request.stockadjustment.CreateStockAdjustmentRequest;
import com.bloom.app.api.dto.request.stockadjustment.FilterStockAdjustmentRequest;
import com.bloom.app.api.dto.response.stockadjustment.CsvParseResponse;
import com.bloom.app.api.dto.response.stockadjustment.CreateStockAdjustmentResponse;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StockAdjustmentService {
    CreateStockAdjustmentResponse createStockAdjustment(CreateStockAdjustmentRequest request);

    Page<StockAdjustmentResponse> filterStockAdjustments(FilterStockAdjustmentRequest request, Pageable pageable);

    StockAdjustmentResponse getStockAdjustmentDetails(String code);

    List<CsvParseResponse> parseCsv(MultipartFile file);

    void downloadTemplate(java.io.OutputStream outputStream);
}
