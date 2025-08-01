package com.bloom.app.api.controller;

import com.bloom.app.api.helper.PagingHelper;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.dto.request.sale.CreateSaleRequest;
import com.bloom.app.domain.dto.request.sale.FilterSaleRequest;
import com.bloom.app.domain.dto.response.ApiResponse;
import com.bloom.app.domain.dto.response.sale.SaleResponse;
import com.bloom.app.service.SaleService;
import com.bloom.app.service.mapper.SaleMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {
    private final SaleService saleService;
    private final SaleMapper saleMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<SaleResponse>> createSale(@Valid @RequestBody CreateSaleRequest request) {
        SaleResponse response = saleService.createSale(request);
        return ResponseHelper.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SaleResponse>>> filterSales(
        FilterSaleRequest request,
        Pageable pageable
    ) {
       Page<SaleResponse> response = saleService.filterSales(request, PagingHelper.toPageRequest(pageable));
       return ResponseHelper.ok(response);
    }

    @GetMapping(path = "/{code}")
    public ResponseEntity<ApiResponse<SaleResponse>> getSaleDetails(@PathVariable String code) {
       SaleResponse response = saleService.getSaleDetails(code);
       return ResponseHelper.ok(response);
    }
}
