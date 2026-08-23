package com.bloom.app.web.controller;

import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.request.sale.FilterSaleRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.service.SaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Validated
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {
    private final SaleService saleService;

    @PostMapping
    public ResponseEntity<ApiResponse<SaleResponse>> createSale(
            @RequestHeader(value = "Idempotency-Key", required = false)
            @NotBlank(message = "Idempotency-Key header is required")
            @Size(max = 100, message = "Idempotency-Key must not exceed 100 characters")
            String checkoutIdempotencyKey,
            @Valid @RequestBody CreateSaleRequest request) {
        SaleResponse response = saleService.createSale(checkoutIdempotencyKey, request);
        return ResponseHelper.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SaleResponse>>> filterSales(
            FilterSaleRequest request,
            Pageable pageable) {
        Page<SaleResponse> response = saleService.filterSales(request, PagingHelper.toPageRequest(pageable));
        return ResponseHelper.ok(response);
    }

    @GetMapping(path = "/details")
    public ResponseEntity<ApiResponse<SaleResponse>> getSaleDetails(@RequestParam String code) {
        SaleResponse response = saleService.getSaleDetails(code);
        return ResponseHelper.ok(response);
    }

    @GetMapping("/export")
    @Operation(summary = "Export sale to Excel", description = "Export a single sale to an Excel file.")
    public ResponseEntity<StreamingResponseBody> exportSale(
            @Parameter(description = "Sale code to export") @RequestParam String code) {
        String filename = String.format("Sale-Export-%s.xlsx", code);

        StreamingResponseBody stream = outputStream -> {
            saleService.exportSale(code, outputStream);
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(stream);
    }

    @GetMapping("/export/bulk")
    @Operation(summary = "Export sales to Zip", description = "Export multiple sales matching the filter criteria to a Zip file containing Excel receipts.")
    public ResponseEntity<StreamingResponseBody> exportSalesBulk(
            @Parameter(description = "Filter parameters for bulk export") FilterSaleRequest request) {
        String filename = String.format("Sales-Bulk-Export-%d.zip", System.currentTimeMillis());

        StreamingResponseBody stream = outputStream -> {
            saleService.exportSalesBulk(request, outputStream);
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(stream);
    }
}
