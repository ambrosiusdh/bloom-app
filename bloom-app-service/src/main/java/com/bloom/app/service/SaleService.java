package com.bloom.app.service;

import com.bloom.app.domain.dto.request.sale.CreateSaleRequest;
import com.bloom.app.domain.dto.request.sale.FilterSaleRequest;
import com.bloom.app.domain.dto.response.sale.SaleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SaleService {
    SaleResponse createSale(CreateSaleRequest request);

    Page<SaleResponse> filterSales(FilterSaleRequest request, Pageable pageable);

    SaleResponse getSaleDetails(String code);
}
