package com.bloom.app.web.controller;

import com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.dto.response.stockmovement.StockMovementResponse;
import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.service.StockMovementQueryService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock-movements")
@RequiredArgsConstructor
public class StockMovementController {
    private final StockMovementQueryService stockMovementQueryService;

    @GetMapping
    @Operation(summary = "Filter stock movements",
        description = "Retrieve the authoritative stock ledger with pagination and filters.")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> filterStockMovements(
            FilterStockMovementRequest request, Pageable pageable) {
        Page<StockMovementResponse> response = stockMovementQueryService.filterMovements(
            request, PagingHelper.toPageRequest(pageable));
        return ResponseHelper.ok(response);
    }
}
