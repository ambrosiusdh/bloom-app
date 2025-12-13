package com.bloom.app.service;

import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.request.sale.FilterSaleRequest;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.OutputStream;

public interface SaleService {
    SaleResponse createSale(CreateSaleRequest request);

    Page<SaleResponse> filterSales(FilterSaleRequest request, Pageable pageable);

    SaleResponse getSaleDetails(String code);

    void exportSale(String code, OutputStream outputStream);

    void exportSalesBulk(FilterSaleRequest request, OutputStream outputStream);
}
