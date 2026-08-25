package com.bloom.app.web.controller;

import com.bloom.app.api.dto.request.stockadjustment.CreateStockAdjustmentRequest;
import com.bloom.app.api.dto.request.stockadjustment.FilterStockAdjustmentRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.stockadjustment.CreateStockAdjustmentResponse;
import com.bloom.app.api.dto.response.stockadjustment.CsvParseResponse;
import com.bloom.app.api.dto.response.stockadjustment.StockAdjustmentResponse;
import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.service.StockAdjustmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequestMapping("/api/stock-adjustments")
@RequiredArgsConstructor
public class StockAdjustmentController {
    private final StockAdjustmentService stockAdjustmentService;

    @PostMapping(path = "/csv-parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Parse CSV for Stock Adjustment", description = "Parse a CSV/Excel file to preview stock adjustment items.")
    public ResponseEntity<ApiResponse<List<CsvParseResponse>>> parseCsv(
        @Parameter(description = "CSV/Excel file to parse") @RequestParam("file") MultipartFile file
    ) {
        List<CsvParseResponse> response = stockAdjustmentService.parseCsv(file);
        return ResponseHelper.ok(response);
    }

    @PostMapping
    @Operation(summary = "Create Stock Adjustment", description = "Create a new stock adjustment transaction.")
    public ResponseEntity<ApiResponse<CreateStockAdjustmentResponse>> createStockAdjustment(
        @Valid @RequestBody CreateStockAdjustmentRequest request
    ) {
        CreateStockAdjustmentResponse response = stockAdjustmentService.createStockAdjustment(request);
        return ResponseHelper.ok(response);
    }

    @GetMapping
    @Operation(summary = "Filter Stock Adjustments", description = "Retrieve a paginated list of stock adjustments based on filters.")
    public ResponseEntity<ApiResponse<Page<StockAdjustmentResponse>>> filterStockAdjustments(
        FilterStockAdjustmentRequest request,
        Pageable pageable
    ) {
        Page<StockAdjustmentResponse> response = stockAdjustmentService.filterStockAdjustments(request,
            PagingHelper.toPageRequest(pageable));
        return ResponseHelper.ok(response);
    }

    @GetMapping("/template")
    @Operation(summary = "Download Excel Template", description = "Download an Excel template for stock adjustment with dropdowns.")
    public ResponseEntity<StreamingResponseBody> downloadTemplate() {
        StreamingResponseBody stream = stockAdjustmentService::downloadTemplate;

        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"stock-adjustment-template.xlsx\"")
            .contentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(stream);
    }

    @GetMapping("/details")
    @Operation(summary = "Get Stock Adjustment Details", description = "Retrieve detailed information about a specific stock adjustment.")
    public ResponseEntity<ApiResponse<StockAdjustmentResponse>> getStockAdjustmentDetails(
        @Parameter(description = "Code of the stock adjustment") @RequestParam String code
    ) {
        StockAdjustmentResponse response = stockAdjustmentService.getStockAdjustmentDetails(code);
        return ResponseHelper.ok(response);
    }
}
