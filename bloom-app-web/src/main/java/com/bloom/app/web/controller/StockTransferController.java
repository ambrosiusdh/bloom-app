package com.bloom.app.web.controller;

import com.bloom.app.api.dto.request.stocktransfer.CreateStockTransferRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.stocktransfer.StockTransferResponse;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.service.StockTransferService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {
    private final StockTransferService stockTransferService;

    @PostMapping
    @Operation(
        summary = "Create Stock Transfer",
        description = "Atomically transfer one or more item quantities between STORE and WAREHOUSE."
    )
    public ResponseEntity<ApiResponse<StockTransferResponse>> createStockTransfer(
        @RequestHeader("Idempotency-Key")
        @NotBlank(message = "Idempotency-Key header is required")
        @Size(max = 100, message = "Idempotency-Key must not exceed 100 characters")
        String requestKey,
        @Valid @RequestBody CreateStockTransferRequest request
    ) {
        StockTransferResponse response = stockTransferService.createStockTransfer(requestKey, request);
        return ResponseHelper.created("Stock transfer created successfully", response);
    }

    @GetMapping("/details")
    @Operation(summary = "Get Stock Transfer Details")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getStockTransferDetails(
        @RequestParam String code
    ) {
        return ResponseHelper.ok(stockTransferService.getStockTransferDetails(code));
    }
}
